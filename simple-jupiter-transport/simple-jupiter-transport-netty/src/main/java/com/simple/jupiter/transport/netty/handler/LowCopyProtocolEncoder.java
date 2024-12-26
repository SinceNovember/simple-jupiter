package com.simple.jupiter.transport.netty.handler;

import com.simple.jupiter.transport.JProtocolHeader;
import com.simple.jupiter.transport.payload.JRequestPayload;
import com.simple.jupiter.transport.payload.JResponsePayload;
import com.simple.jupiter.transport.payload.PayloadHolder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.EncoderException;

/**
 * <pre>
 * **************************************************************************************************
 *                                          Protocol
 *  ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐
 *       2   │   1   │    1   │     8     │      4      │
 *  ├ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┤
 *           │       │        │           │             │
 *  │  MAGIC   Sign    Status   Invoke Id    Body Size                    Body Content              │
 *           │       │        │           │             │
 *  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘
 *
 * 消息头16个字节定长
 * = 2 // magic = (short) 0xbabe
 * + 1 // 消息标志位, 低地址4位用来表示消息类型request/response/heartbeat等, 高地址4位用来表示序列化类型
 * + 1 // 状态位, 设置请求响应状态
 * + 8 // 消息 id, long 类型, 未来jupiter可能将id限制在48位, 留出高地址的16位作为扩展字段
 * + 4 // 消息体 body 长度, int 类型
 * </pre>
 *
 * jupiter
 * org.jupiter.transport.netty.handler
 *
 * @author jiachun.fjc
 */
@ChannelHandler.Sharable
public class LowCopyProtocolEncoder extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        ByteBuf buf = null;
        try {
            if (msg instanceof PayloadHolder) {
                PayloadHolder cast = (PayloadHolder) msg;
                buf = encode(cast);
                ctx.write(buf, promise);
                buf = null;
            } else {
                ctx.write(msg, promise);
            }
        } catch (Throwable t) {
            throw new EncoderException(t);
        } finally {
            if (buf != null) {
                buf.release();
            }
        }
    }

    protected ByteBuf encode(PayloadHolder msg) throws Exception {
        if (msg instanceof JRequestPayload) {
            return doEncodeRequest((JRequestPayload) msg);
        } else if (msg instanceof JResponsePayload) {
            return doEncodeResponse((JResponsePayload) msg);
        } else {
            throw new IllegalArgumentException(Reflects.simpleClassName(msg));
        }
    }

    private ByteBuf doEncodeRequest(JRequestPayload request) {
        byte sign = JProtocolHeader.toSign(request.serializerCode(), JProtocolHeader.REQUEST);
        long invokeId = request.invokeId();
        //这边获取的是NettyOutput那边生成好并前面空16个位置 + 后面填充了具体的请求内容的ByteBuf, 所以这边补齐除了内容之外的byte
        ByteBuf byteBuf = (ByteBuf) request.outputBuf().backingObject();
        int length = byteBuf.readableBytes();
        //标记当前位置，等补齐header后还要回到消息的末尾
        byteBuf.markWriterIndex();
        //将index移动到开头，用来补齐header
        byteBuf.writerIndex(byteBuf.writerIndex() - length);
        //补充除了内容之外的数据
        byteBuf.writeShort(JProtocolHeader.MAGIC)
            .writeByte(sign)
            .writeByte(0x00)
            .writeLong(invokeId)
            .writeInt(length - JProtocolHeader.HEADER_SIZE);

        //回到消息的末尾
        byteBuf.resetWriterIndex();
        return byteBuf;
    }

    private ByteBuf doEncodeResponse(JResponsePayload response) {
        byte sign = JProtocolHeader.toSign(response.serializerCode(), JProtocolHeader.RESPONSE);
        byte status = response.status();
        long invokeId = response.id();
        ByteBuf byteBuf = (ByteBuf) response.outputBuf().backingObject();
        int length = byteBuf.readableBytes();

        byteBuf.markWriterIndex();

        byteBuf.writerIndex(byteBuf.writerIndex() - length);

        byteBuf.writeShort(JProtocolHeader.MAGIC)
            .writeByte(sign)
            .writeByte(status)
            .writeLong(invokeId)
            .writeInt(length - JProtocolHeader.HEADER_SIZE);

        byteBuf.resetWriterIndex();

        return byteBuf;
    }
}
