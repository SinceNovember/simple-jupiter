package com.simple.jupiter.transport.netty.handler;

import com.simple.jupiter.transport.JProtocolHeader;
import com.simple.jupiter.transport.payload.JRequestPayload;
import com.simple.jupiter.transport.payload.JResponsePayload;
import com.simple.jupiter.transport.payload.PayloadHolder;
import com.simple.jupiter.util.Reflects;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

@ChannelHandler.Sharable
public class ProtocolEncoder extends MessageToByteEncoder<PayloadHolder> {

    @Override
    protected void encode(ChannelHandlerContext ctx, PayloadHolder msg, ByteBuf out) throws Exception {
        if (msg instanceof JRequestPayload) {
            doEncodeRequest((JRequestPayload) msg, out);
        } else if (msg instanceof JResponsePayload) {
            doEncodeResponse((JResponsePayload) msg, out);
        } else {
            throw new IllegalArgumentException(Reflects.simpleClassName(msg));
        }
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, PayloadHolder msg, boolean preferDirect) throws Exception {
        if (preferDirect) {
            return ctx.alloc().ioBuffer(JProtocolHeader.HEADER_SIZE + msg.size());
        } else {
            return ctx.alloc().heapBuffer(JProtocolHeader.HEADER_SIZE + msg.size());
        }
    }

    private void doEncodeRequest(JRequestPayload request, ByteBuf out) {
        byte sign = JProtocolHeader.toSign(request.serializerCode(), JProtocolHeader.REQUEST);
        long invokeId = request.invokeId();
        byte[] bytes = request.bytes();
        int length = bytes.length;

        out.writeShort(JProtocolHeader.MAGIC)
                .writeByte(sign)
                .writeByte(0x00)
                .writeLong(invokeId)
                .writeInt(length)
                .writeBytes(bytes);
    }

    private void doEncodeResponse(JResponsePayload response, ByteBuf out) {
        byte sign = JProtocolHeader.toSign(response.serializerCode(), JProtocolHeader.RESPONSE);
        byte status = response.status();
        long invokeId = response.id();
        byte[] bytes = response.bytes();
        int length = bytes.length;

        out.writeShort(JProtocolHeader.MAGIC)
                .writeByte(sign)
                .writeByte(status)
                .writeLong(invokeId)
                .writeInt(length)
                .writeBytes(bytes);
    }
}
