package com.simple.jupiter.rpc.provider.processor;

import com.simple.jupiter.rpc.JRequest;
import com.simple.jupiter.rpc.executor.CloseableExecutor;
import com.simple.jupiter.rpc.flow.control.FlowController;
import com.simple.jupiter.rpc.model.metadata.ResultWrapper;
import com.simple.jupiter.rpc.provider.LookupService;
import com.simple.jupiter.rpc.provider.processor.task.MessageTask;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerFactory;
import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.transport.CodecConfig;
import com.simple.jupiter.transport.Status;
import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.channel.JFutureListener;
import com.simple.jupiter.transport.payload.JRequestPayload;
import com.simple.jupiter.transport.payload.JResponsePayload;
import com.simple.jupiter.transport.processor.ProviderProcessor;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.ThrowUtil;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

public abstract class DefaultProviderProcessor implements ProviderProcessor, LookupService, FlowController<JRequest> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultProviderProcessor.class);

    private final CloseableExecutor executor;

    public DefaultProviderProcessor() {
        this(ProviderExecutors.executor());
    }

    public DefaultProviderProcessor(CloseableExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void handleRequest(JChannel channel, JRequestPayload requestPayload) throws Exception {
        MessageTask task = new MessageTask(this, channel, new JRequest(requestPayload));
        //未定义线程池的话，交给Channel对应的EventLoop线程来处理该任务（单线程循环执行，不会有线程安全）
        if (executor == null) {
            channel.addTask(task);
        } else {
            executor.execute(task);
        }
    }

    @Override
    public void handleException(JChannel channel, JRequestPayload request, Status status, Throwable cause) {
        logger.error("An exception was caught while processing request: {}, {}.",
                channel.remoteAddress(), StackTraceUtil.stackTrace(cause));

        doHandleException(
                channel, request.invokeId(), request.serializerCode(), status.value(), cause, false);
    }

    @Override
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }

    public void handleException(JChannel channel, JRequest request, Status status, Throwable cause) {
        logger.error("An exception was caught while processing request: {}, {}.",
                channel.remoteAddress(), StackTraceUtil.stackTrace(cause));

        doHandleException(
                channel, request.invokeId(), request.serializerCode(), status.value(), cause, false);
    }

    public void handleRejected(JChannel channel, JRequest request, Status status, Throwable cause) {
        if (logger.isWarnEnabled()) {
            logger.warn("Service rejected: {}, {}.", channel.remoteAddress(), StackTraceUtil.stackTrace(cause));
        }

        doHandleException(
                channel, request.invokeId(), request.serializerCode(), status.value(), cause, true);
    }

    private void doHandleException(
            JChannel channel, long invokeId, byte s_code, byte status, Throwable cause, boolean closeChannel) {

        ResultWrapper result = new ResultWrapper();
        // 截断cause, 避免客户端无法找到cause类型而无法序列化
        result.setError(ThrowUtil.cutCause(cause));

        Serializer serializer = SerializerFactory.getSerializer(s_code);

        JResponsePayload response = new JResponsePayload(invokeId);
        response.status(status);
        if (CodecConfig.isCodecLowCopy()) {
            OutputBuf outputBuf =
                    serializer.writeObject(channel.allocOutputBuf(), result);
            response.outputBuf(s_code, outputBuf);
        } else {
            byte[] bytes = serializer.writeObject(result);
            response.bytes(s_code, bytes);
        }

        if (closeChannel) {
            channel.write(response, JChannel.CLOSE);
        } else {
            channel.write(response, new JFutureListener<JChannel>() {

                @Override
                public void operationSuccess(JChannel channel) throws Exception {
                    logger.debug("Service error message sent out: {}.", channel);
                }

                @Override
                public void operationFailure(JChannel channel, Throwable cause) throws Exception {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Service error message sent failed: {}, {}.", channel,
                                StackTraceUtil.stackTrace(cause));
                    }
                }
            });
        }
    }



}
