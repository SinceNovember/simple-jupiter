package com.simple.jupiter.rpc.consumer.dispatcher;

import com.simple.jupiter.rpc.DispatchType;
import com.simple.jupiter.rpc.JClient;
import com.simple.jupiter.rpc.JRequest;
import com.simple.jupiter.rpc.consumer.future.DefaultInvokeFuture;
import com.simple.jupiter.rpc.consumer.future.DefaultInvokeFutureGroup;
import com.simple.jupiter.rpc.consumer.future.InvokeFuture;
import com.simple.jupiter.rpc.model.metadata.MessageWrapper;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerType;
import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.transport.CodecConfig;
import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.channel.JChannelGroup;

public class DefaultBroadcastDispatcher extends AbstractDispatcher{
    public DefaultBroadcastDispatcher(JClient client,
                                      SerializerType serializerType) {
        super(client, serializerType);
    }

    /**
     * 实际像服务端提供端发送消息的入口（广播发送每个服务提供段都发送）
     * @param request
     * @param returnType
     * @return
     * @param <T>
     */
    @Override
    public <T> InvokeFuture<T> dispatch(JRequest request, Class<T> returnType) {
        // stack copy
        final Serializer _serializer = serializer();
        final MessageWrapper message = request.message();

        JChannelGroup[] groups = groups(message.getMetadata());
        JChannel[] channels = new JChannel[groups.length];
        for (int i = 0; i < groups.length; i++) {
            channels[i] = groups[i].next();
        }

        byte s_code = _serializer.code();
        // 在业务线程中序列化, 减轻IO线程负担
        boolean isLowCopy = CodecConfig.isCodecLowCopy();
        if (!isLowCopy) {
            byte[] bytes = _serializer.writeObject(message);
            request.bytes(s_code, bytes);
        }

        DefaultInvokeFuture<T>[] futures = new DefaultInvokeFuture[channels.length];
        //像每个服务提供段发送消息
        for (int i = 0; i < channels.length; i++) {
            JChannel channel = channels[i];
            if (isLowCopy) {
                OutputBuf outputBuf =
                    _serializer.writeObject(channel.allocOutputBuf(), message);
                request.outputBuf(s_code, outputBuf);
            }
            futures[i] = write(channel, request, returnType, DispatchType.BROADCAST);
        }

        return DefaultInvokeFutureGroup.with(futures);
    }
}
