package com.simple.jupiter.rpc.provider.processor.task;

import com.simple.jupiter.concurrent.RejectedRunnable;
import com.simple.jupiter.rpc.*;
import com.simple.jupiter.rpc.exception.*;
import com.simple.jupiter.rpc.flow.control.ControlResult;
import com.simple.jupiter.rpc.flow.control.FlowController;
import com.simple.jupiter.rpc.model.metadata.MessageWrapper;
import com.simple.jupiter.rpc.model.metadata.ResultWrapper;
import com.simple.jupiter.rpc.model.metadata.ServiceWrapper;
import com.simple.jupiter.rpc.provider.ProviderInterceptor;
import com.simple.jupiter.rpc.provider.processor.DefaultProviderProcessor;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerFactory;
import com.simple.jupiter.serialization.io.InputBuf;
import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.transport.CodecConfig;
import com.simple.jupiter.transport.Status;
import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.channel.JFutureListener;
import com.simple.jupiter.transport.payload.JRequestPayload;
import com.simple.jupiter.transport.payload.JResponsePayload;
import com.simple.jupiter.util.*;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class MessageTask implements RejectedRunnable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MessageTask.class);

    private static final boolean METRIC_NEEDED = SystemPropertyUtil.getBoolean("jupiter.metric.needed", false);

    private static final Signal INVOKE_ERROR = Signal.valueOf(MessageTask.class, "INVOKE_ERROR");


    private final DefaultProviderProcessor processor;
    private final JChannel channel;
    private final JRequest request;

    public MessageTask(DefaultProviderProcessor processor, JChannel channel, JRequest request) {
        this.processor = processor;
        this.channel = channel;
        this.request = request;
    }


    @Override
    public void rejected() {
        rejected(Status.SERVER_BUSY, new JupiterServerBusyException(String.valueOf(request)));
    }

    private void rejected(Status status, JupiterRemoteException cause) {

        // 当服务拒绝方法被调用时一般分以下几种情况:
        //  1. 非法请求, close当前连接;
        //  2. 服务端处理能力出现瓶颈, close当前连接, jupiter客户端会自动重连, 在加权负载均衡的情况下权重是一点一点升上来的.
        processor.handleRejected(channel, request, status, cause);
    }

    @Override
    public void run() {
        // stack copy
        final DefaultProviderProcessor _processor = processor;
        final JRequest _request = request;
        // 全局流量控制
        ControlResult ctrl = _processor.flowControl(_request);
        if (!ctrl.isAllowed()) {
            rejected(Status.APP_FLOW_CONTROL, new JupiterFlowControlException(String.valueOf(ctrl)));
            return;
        }

        MessageWrapper msg;
        try {
            JRequestPayload _requestPayload = _request.payload();

            byte s_code = _requestPayload.serializerCode();
            Serializer serializer = SerializerFactory.getSerializer(s_code);

            // 在业务线程中反序列化, 减轻IO线程负担
            if (CodecConfig.isCodecLowCopy()) {
                InputBuf inputBuf = _requestPayload.inputBuf();
                msg = serializer.readObject(inputBuf, MessageWrapper.class);
            } else {
                byte[] bytes = _requestPayload.bytes();
                msg = serializer.readObject(bytes, MessageWrapper.class);
            }
            _requestPayload.clear();

            _request.message(msg);
        } catch (Throwable t) {
            rejected(Status.BAD_REQUEST, new JupiterBadRequestException("reading request failed", t));
            return;
        }

        // 查找服务
        final ServiceWrapper service = _processor.lookupService(msg.getMetadata());
        if (service == null) {
            rejected(Status.SERVICE_NOT_FOUND, new JupiterServiceNotFoundException(String.valueOf(msg)));
            return;
         }

        // provider私有流量控制
        FlowController<JRequest> childController = service.getFlowController();
        if (childController != null) {
            ctrl = childController.flowControl(_request);
            if (!ctrl.isAllowed()) {
                rejected(Status.PROVIDER_FLOW_CONTROL, new JupiterFlowControlException(String.valueOf(ctrl)));
                return;
            }
        }

        // processing
        Executor childExecutor = service.getExecutor();
        if (childExecutor == null) {
            process(service);
        } else {
            // provider私有线程池执行
            childExecutor.execute(() -> process(service));
        }
    }

    private void process(ServiceWrapper service) {
        final Context invokeCtx = new Context(service);
        try {
            final Object invokeResult = Chains.invoke(request, invokeCtx)
                    .getResult();

            if (!(invokeResult instanceof CompletableFuture)) {
                doProcess(invokeResult);
                return;
            }

            CompletableFuture<Object> cf = (CompletableFuture<Object>) invokeResult;

            if (cf.isDone()) {
                doProcess(cf.join());
                return;
            }
            cf.whenComplete((result, throwable) -> {
                if (throwable == null) {
                    try {
                        doProcess(result);
                    } catch (Throwable t) {
                        handleFail(invokeCtx, t);
                    }
                } else {
                    handleFail(invokeCtx, throwable);
                }
            });
        } catch (Throwable t) {
            handleFail(invokeCtx, t);
        }

    }

    private void doProcess(Object realResult) {
        ResultWrapper result = new ResultWrapper();
        result.setResult(realResult);
        byte s_code = request.serializerCode();
        Serializer serializer = SerializerFactory.getSerializer(s_code);

        JResponsePayload responsePayload = new JResponsePayload(request.invokeId());

        if (CodecConfig.isCodecLowCopy()) {
            OutputBuf outputBuf =
                    serializer.writeObject(channel.allocOutputBuf(), result);
            responsePayload.outputBuf(s_code, outputBuf);
        } else {
            byte[] bytes = serializer.writeObject(result);
            responsePayload.bytes(s_code, bytes);
        }

        responsePayload.status(Status.OK.value());

        handleWriteResponse(responsePayload);
    }

    private void handleFail(Context invokeCtx, Throwable t) {
        if (INVOKE_ERROR == t) {
            // handle biz exception
            handleException(invokeCtx.getExpectCauseTypes(), invokeCtx.getCause());
        } else {
            processor.handleException(channel, request, Status.SERVER_ERROR, t);
        }
    }

    private void handleException(Class<?>[] exceptionTypes, Throwable failCause) {
        if (exceptionTypes != null && exceptionTypes.length > 0) {
            Class<?> failType = failCause.getClass();
            for (Class<?> eType : exceptionTypes) {
                // 如果抛出声明异常的子类, 客户端可能会因为不存在子类类型而无法序列化, 会在客户端抛出无法反序列化异常
                if (eType.isAssignableFrom(failType)) {
                    // 预期内的异常
                    processor.handleException(channel, request, Status.SERVICE_EXPECTED_ERROR, failCause);
                    return;
                }
            }
        }
    }

    private void handleWriteResponse(JResponsePayload response) {
        channel.write(response, new JFutureListener<JChannel>() {

            @Override
            public void operationSuccess(JChannel channel) throws Exception {
                if (METRIC_NEEDED) {
                    long duration = SystemClock.millisClock().now() - request.timestamp();
                }
            }

            @Override
            public void operationFailure(JChannel channel, Throwable cause) throws Exception {
                long duration = SystemClock.millisClock().now() - request.timestamp();
                logger.error("Response sent failed, duration: {} millis, channel: {}, cause: {}.",
                        duration, channel, cause);
            }
        });
    }


    public static class Context implements JFilterContext {

        private final ServiceWrapper service;

        private Object result;                  // 服务调用结果
        private Throwable cause;                // 业务异常
        private Class<?>[] expectCauseTypes;    // 预期内的异常类型

        public Context(ServiceWrapper service) {
            this.service = Requires.requireNotNull(service, "service");
        }

        public ServiceWrapper getService() {
            return service;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }

        public Throwable getCause() {
            return cause;
        }

        public Class<?>[] getExpectCauseTypes() {
            return expectCauseTypes;
        }

        public void setCauseAndExpectTypes(Throwable cause, Class<?>[] expectCauseTypes) {
            this.cause = cause;
            this.expectCauseTypes = expectCauseTypes;
        }

        @Override
        public JFilter.Type getType() {
            return JFilter.Type.PROVIDER;
        }
    }


    static class InterceptorsFilter implements JFilter {

        @Override
        public Type getType() {
            return Type.PROVIDER;
        }

        @Override
        public <T extends JFilterContext> void doFilter(JRequest request, T filterCtx, JFilterChain next) throws Throwable {
            Context invokeCtx = (Context) filterCtx;
            ServiceWrapper service = invokeCtx.getService();
            // 拦截器
            ProviderInterceptor[] interceptors = service.getInterceptors();

            if (interceptors == null || interceptors.length == 0) {
                next.doFilter(request, filterCtx);
            } else {
                Object provider = service.getServiceProvider();
                MessageWrapper msg = request.message();
                String methodName = msg.getMethodName();
                Object[] args = msg.getArgs();
                handleBeforeInvoke(interceptors, provider, methodName, args);
                try {
                    next.doFilter(request, filterCtx);
                } finally {
                    handleAfterInvoke(
                            interceptors, provider, methodName, args, invokeCtx.getResult(), invokeCtx.getCause());
                }
            }
        }
    }

    static class InvokeFilter implements JFilter {


        @Override
        public Type getType() {
            return Type.PROVIDER;
        }

        @Override
        public <T extends JFilterContext> void doFilter(JRequest request, T filterCtx, JFilterChain next) throws Throwable {
            MessageWrapper msg = request.message();
            Context invokeCtx = (Context) filterCtx;
            Object invokeResult = MessageTask.invoke(msg, invokeCtx);
            invokeCtx.setResult(invokeResult);
        }
    }

    private static Object invoke(MessageWrapper msg, Context invokeCtx) throws Signal {
        ServiceWrapper service = invokeCtx.getService();
        Object provider = service.getServiceProvider();
        String methodName = msg.getMethodName();
        Object[] args = msg.getArgs();

        Class<?>[] expectCauseTypes = null;
        try {
            List<Pair<Class<?>[], Class<?>[]>> methodExtension = service.getMethodExtension(methodName);
            if (methodExtension == null) {
                throw new NoSuchMethodException(methodName);
            }

            // 根据JLS方法调用的静态分派规则查找最匹配的方法parameterTypes
            Pair<Class<?>[], Class<?>[]> bestMatch = Reflects.findMatchingParameterTypesExt(methodExtension, args);
            Class<?>[] parameterTypes = bestMatch.getFirst();
            expectCauseTypes = bestMatch.getSecond();

            return Reflects.fastInvoke(provider, methodName, parameterTypes, args);
        } catch (Throwable t) {
            invokeCtx.setCauseAndExpectTypes(t, expectCauseTypes);
            throw INVOKE_ERROR;
        }
    }

    private static void handleBeforeInvoke(ProviderInterceptor[] interceptors,
                                           Object provider,
                                           String methodName,
                                           Object[] args) {

        for (int i = 0; i < interceptors.length; i++) {
            try {
                interceptors[i].beforeInvoke(provider, methodName, args);
            } catch (Throwable t) {
                logger.error("Interceptor[{}#beforeInvoke]: {}.", Reflects.simpleClassName(interceptors[i]),
                        StackTraceUtil.stackTrace(t));
            }
        }
    }

    private static void handleAfterInvoke(ProviderInterceptor[] interceptors,
                                          Object provider,
                                          String methodName,
                                          Object[] args,
                                          Object invokeResult,
                                          Throwable failCause) {

        for (int i = interceptors.length - 1; i >= 0; i--) {
            try {
                interceptors[i].afterInvoke(provider, methodName, args, invokeResult, failCause);
            } catch (Throwable t) {
                logger.error("Interceptor[{}#afterInvoke]: {}.", Reflects.simpleClassName(interceptors[i]),
                        StackTraceUtil.stackTrace(t));
            }
        }
    }

    static class Chains {
        private static final JFilterChain headChain;

        static {
            JFilterChain invokeChain = new DefaultFilterChain(new InvokeFilter(), null);
            JFilterChain interceptChain = new DefaultFilterChain(new InterceptorsFilter(), invokeChain);
            headChain = JFilterLoader.loadExtFilters(interceptChain, JFilter.Type.PROVIDER);
        }

        static <T extends JFilterContext> T invoke(JRequest request, T invokeCtx) throws Throwable {
            headChain.doFilter(request, invokeCtx);
            return invokeCtx;
        }
    }
}
