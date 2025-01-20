package com.simple.jupiter.rpc.consumer.processor.task;

import com.simple.jupiter.rpc.JResponse;
import com.simple.jupiter.rpc.exception.JupiterSerializationException;
import com.simple.jupiter.rpc.model.metadata.ResultWrapper;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerFactory;
import com.simple.jupiter.serialization.io.InputBuf;
import com.simple.jupiter.transport.CodecConfig;
import com.simple.jupiter.transport.Status;
import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.payload.JResponsePayload;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

public class MessageTask implements Runnable {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MessageTask.class);

    private final JChannel channel;
    private final JResponse response;


    public MessageTask(JChannel channel, JResponse response) {
        this.channel = channel;
        this.response = response;
    }

    @Override
    public void run() {
        // stack copy
        final JResponse _response = response;
        final JResponsePayload _responsePayload = _response.payload();
        byte s_code = _response.serializerCode();

        Serializer serializer = SerializerFactory.getSerializer(s_code);
        ResultWrapper wrapper;
        try {
            if (CodecConfig.isCodecLowCopy()) {
                InputBuf inputBuf = _responsePayload.inputBuf();
                wrapper = serializer.readObject(inputBuf, ResultWrapper.class);
            } else {
                byte[] bytes = _responsePayload.bytes();
                wrapper = serializer.readObject(bytes, ResultWrapper.class);
            }
            _responsePayload.clear();
        } catch (Throwable t) {
            logger.error("Deserialize object failed: {}, {}.", channel.remoteAddress(), StackTraceUtil.stackTrace(t));

            _response.status(Status.DESERIALIZATION_FAIL);
            wrapper = new ResultWrapper();
            wrapper.setError(new JupiterSerializationException(t));
        }
        _response.result(wrapper);
    }
}
