package com.simple.jupiter.rpc.consumer.dispatcher;

import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import com.simple.jupiter.rpc.DispatchType;
import com.simple.jupiter.rpc.JClient;
import com.simple.jupiter.rpc.JRequest;
import com.simple.jupiter.rpc.JResponse;
import com.simple.jupiter.rpc.consumer.ConsumerInterceptor;
import com.simple.jupiter.rpc.consumer.future.DefaultInvokeFuture;
import com.simple.jupiter.rpc.exception.JupiterRemoteException;
import com.simple.jupiter.rpc.load.balance.LoadBalancer;
import com.simple.jupiter.rpc.model.metadata.MessageWrapper;
import com.simple.jupiter.rpc.model.metadata.MethodSpecialConfig;
import com.simple.jupiter.rpc.model.metadata.ResultWrapper;
import com.simple.jupiter.rpc.model.metadata.ServiceMetadata;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerFactory;
import com.simple.jupiter.serialization.SerializerType;
import com.simple.jupiter.transport.Status;
import com.simple.jupiter.transport.channel.CopyOnWriteGroupList;
import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.channel.JChannelGroup;
import com.simple.jupiter.transport.channel.JFutureListener;
import com.simple.jupiter.transport.payload.JRequestPayload;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.Maps;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.SystemClock;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

public abstract class AbstractDispatcher implements Dispatcher {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractDispatcher.class);

    private final JClient client;

    private final LoadBalancer loadBalancer;                    // 软负载均衡

    private final Serializer serializerImpl;                    // 序列化/反序列化impl

    private ConsumerInterceptor[] interceptors;                 // 消费者端拦截器

    private long timeoutMillis = JConstants.DEFAULT_TIMEOUT;    // 调用超时时间设置
    // 针对指定方法单独设置的超时时间, 方法名为key, 方法参数类型不做区别对待
    private Map<String, Long> methodSpecialTimeoutMapping = Maps.newHashMap();

    public AbstractDispatcher(JClient client, SerializerType serializerType) {
        this(client, null, serializerType);
    }

    public AbstractDispatcher(JClient client, LoadBalancer loadBalancer, SerializerType serializerType) {
        this.client = client;
        this.loadBalancer = loadBalancer;
        this.serializerImpl = SerializerFactory.getSerializer(serializerType.value());
    }

    public Serializer serializer() {
        return serializerImpl;
    }

    public ConsumerInterceptor[] interceptors() {
        return interceptors;
    }

    @Override
    public Dispatcher interceptors(List<ConsumerInterceptor> interceptors) {
        if (interceptors != null && !interceptors.isEmpty()) {
            this.interceptors = interceptors.toArray(new ConsumerInterceptor[0]);
        }
        return this;
    }

    @Override
    public Dispatcher timeoutMillis(long timeoutMillis) {
        if (timeoutMillis > 0) {
            this.timeoutMillis = timeoutMillis;
        }
        return this;
    }

    @Override
    public Dispatcher methodSpecialConfigs(List<MethodSpecialConfig> methodSpecialConfigs) {
        if (!methodSpecialConfigs.isEmpty()) {
            for (MethodSpecialConfig config : methodSpecialConfigs) {
                long timeoutMillis = config.getTimeoutMillis();
                if (timeoutMillis > 0) {
                    methodSpecialTimeoutMapping.put(config.getMethodName(), timeoutMillis);
                }
            }
        }
        return this;
    }

    protected long getMethodSpecialTimeoutMillis(String methodName) {
        Long methodTimeoutMillis = methodSpecialTimeoutMapping.get(methodName);
        if (methodTimeoutMillis != null && methodTimeoutMillis > 0) {
            return methodTimeoutMillis;
        }
        return timeoutMillis;
    }

    /**
     * 根据元数据获取合适的服务提供端已经合适的Channel
     * @param metadata
     * @return
     */
    protected JChannel select(ServiceMetadata metadata) {
        //获取注册了对应的元数据信息的服务器提供端组
        CopyOnWriteGroupList groups = client
            .connector()
            .directory(metadata);
        //根据负载均衡策略找到的合适的服务提供端的所有Channel
        JChannelGroup group = loadBalancer.select(groups, metadata);
        if (group != null) {
            if (group.isAvailable()) {
                return group.next();
            }

            // to the deadline (no available channel), the time exceeded the predetermined limit
            long deadline = group.deadlineMillis();
            if (deadline > 0 && SystemClock.millisClock().now() > deadline) {
                boolean removed = groups.remove(group);
                if (removed) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Removed channel group: {} in directory: {} on [select].",
                            group, metadata.directoryString());
                    }
                }
            }
        } else {
            // for 3 seconds, expired not wait
            if (!client.awaitConnections(metadata, 3000)) {
                throw new IllegalStateException("No connections");
            }
        }
        JChannelGroup[] snapshot = groups.getSnapshot();
        for (JChannelGroup g : snapshot) {
            if (g.isAvailable()) {
                return g.next();
            }
        }

        throw new IllegalStateException("No channel");
    }

    protected JChannelGroup[] groups(ServiceMetadata metadata) {
        return client.connector()
            .directory(metadata)
            .getSnapshot();
    }

    protected <T> DefaultInvokeFuture<T> write(
        final JChannel channel, final JRequest request, final Class<T> returnType, final DispatchType dispatchType) {
        final MessageWrapper message = request.message();
        final long timeoutMillis = getMethodSpecialTimeoutMillis(message.getMethodName());
        final ConsumerInterceptor[] interceptors = interceptors();
        final DefaultInvokeFuture<T> future = DefaultInvokeFuture
            .with(request.invokeId(), channel, timeoutMillis, returnType, dispatchType)
            .interceptors(interceptors);

        if (interceptors != null) {
            for (int i = 0; i < interceptors.length; i++) {
                interceptors[i].beforeInvoke(request, channel);
            }
        }

        final JRequestPayload payload = request.payload();

        channel.write(payload, new JFutureListener<JChannel>() {
            @Override
            public void operationSuccess(JChannel channel) throws Exception {
                // 标记已发送
                future.markSent();

                if (dispatchType == DispatchType.ROUND) {
                    payload.clear();
                }
            }

            @Override
            public void operationFailure(JChannel channel, Throwable cause) throws Exception {
                if (dispatchType == DispatchType.ROUND) {
                    payload.clear();
                }

                if (logger.isWarnEnabled()) {
                    logger.warn("Writes {} fail on {}, {}.", request, channel, StackTraceUtil.stackTrace(cause));
                }

                ResultWrapper result = new ResultWrapper();
                result.setError(new JupiterRemoteException(cause));

                JResponse response = new JResponse(payload.invokeId());
                response.status(Status.CLIENT_ERROR);
                response.result(result);

                DefaultInvokeFuture.fakeReceived(channel, response, dispatchType);
            }
        });

        return future;
    }

}
