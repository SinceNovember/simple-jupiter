package com.simple.jupiter.rpc.consumer.dispatcher;

import com.simple.jupiter.rpc.DispatchType;
import com.simple.jupiter.rpc.JClient;
import com.simple.jupiter.rpc.JRequest;
import com.simple.jupiter.rpc.consumer.future.InvokeFuture;
import com.simple.jupiter.rpc.load.balance.LoadBalancer;
import com.simple.jupiter.rpc.model.metadata.MessageWrapper;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerType;
import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.transport.CodecConfig;
import com.simple.jupiter.transport.channel.JChannel;

/**
 * 单播发送消息，根据提供的负载均衡策略，选择一个连接发送消息
 */
public class DefaultRoundDispatcher extends AbstractDispatcher {
    public DefaultRoundDispatcher(JClient client,
                                  LoadBalancer loadBalancer,
                                  SerializerType serializerType) {
        super(client, loadBalancer, serializerType);
    }

    @Override
    public <T> InvokeFuture<T> dispatch(JRequest request, Class<T> returnType) {
        // stack copy
        final Serializer _serializer = serializer();
        final MessageWrapper message = request.message();

        // 通过软负载均衡选择一个channel
        JChannel channel = select(message.getMetadata());
        byte s_code = _serializer.code();

        // 在业务线程中序列化, 减轻IO线程负担
        if (CodecConfig.isCodecLowCopy()) {
            OutputBuf outputBuf =
                _serializer.writeObject(channel.allocOutputBuf(), message);
            request.outputBuf(s_code, outputBuf);
        } else {
            byte[] bytes = _serializer.writeObject(message);
            request.bytes(s_code, bytes);
        }

        return write(channel, request, returnType, DispatchType.ROUND);

    }
}
