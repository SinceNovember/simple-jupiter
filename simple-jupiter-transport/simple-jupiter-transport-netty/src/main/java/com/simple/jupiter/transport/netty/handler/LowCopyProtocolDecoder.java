package com.simple.jupiter.transport.netty.handler;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;
import com.simple.jupiter.serialization.io.InputBuf;
import com.simple.jupiter.transport.JProtocolHeader;
import com.simple.jupiter.transport.exception.IoSignals;
import com.simple.jupiter.transport.payload.JRequestPayload;
import com.simple.jupiter.transport.payload.JResponsePayload;
import com.simple.jupiter.util.Signal;
import com.simple.jupiter.util.SystemClock;
import com.simple.jupiter.util.SystemPropertyUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ReplayingDecoder;

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
public class LowCopyProtocolDecoder extends ReplayingDecoder<LowCopyProtocolDecoder.State> {


    // 协议体最大限制, 默认5M
    private static final int MAX_BODY_SIZE = SystemPropertyUtil.getInt("jupiter.io.decoder.max.body.size", 1024 * 1024 * 5);

    private static final boolean USE_COMPOSITE_BUF = SystemPropertyUtil.getBoolean("jupiter.io.decoder.composite.buf", false);

    public LowCopyProtocolDecoder() {
        super(State.MAGIC);
        if (USE_COMPOSITE_BUF) {
            setCumulator(COMPOSITE_CUMULATOR);
        }
    }

    // 协议头
    private final JProtocolHeader header = new JProtocolHeader();

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out)
        throws Exception {
        switch (state()) {
            case MAGIC:
                checkMagic(in.readShort());         // MAGIC  如果数据不足，这里会抛出 NEED_MORE_DATA 异常
                checkpoint(State.SIGN);             //记录下面获取的状态塞到state()中 如果上面读数据抛出异常的话 会挂起后通知netty等有足够数据后重新调用decode方法
            case SIGN:
                header.sign(in.readByte());         // 消息标志位
                checkpoint(State.STATUS);
            case STATUS:
                header.status(in.readByte());       // 状态位
                checkpoint(State.ID);
            case ID:
                header.id(in.readLong());           // 消息id
                checkpoint(State.BODY_SIZE);
            case BODY_SIZE:
                header.bodySize(in.readInt());      // 消息体长度
                checkpoint(State.BODY);
            case BODY:
                switch (header.messageCode()) {
                    case JProtocolHeader.HEARTBEAT:
                        break;
                    case JProtocolHeader.REQUEST: {
                        int length = checkBodySize(header.bodySize());
                        ByteBuf bodyByteBuf = in.readRetainedSlice(length);

                        JRequestPayload request = new JRequestPayload(header.id());
                        request.timestamp(SystemClock.millisClock().now());
                        request.inputBuf(header.serializerCode(), new NettyInputBuf(bodyByteBuf));
                        out.add(request);

                        break;
                    }
                    case JProtocolHeader.RESPONSE: {
                        int length = checkBodySize(header.bodySize());
                        ByteBuf bodyByteBuf = in.readRetainedSlice(length);

                        JResponsePayload response = new JResponsePayload(header.id());
                        response.status(header.status());
                        response.inputBuf(header.serializerCode(), new NettyInputBuf(bodyByteBuf));

                        out.add(response);

                        break;
                    }
                    default:
                        throw IoSignals.ILLEGAL_SIGN;
                }
                checkpoint(State.MAGIC);
        }
    }

    private static void checkMagic(short magic) throws Signal {
        if (magic != JProtocolHeader.MAGIC) {
            throw IoSignals.ILLEGAL_MAGIC;
        }
    }

    private static int checkBodySize(int size) throws Signal {
        if (size > MAX_BODY_SIZE) {
            throw IoSignals.BODY_TOO_LARGE;
        }
        return size;
    }


    static final class NettyInputBuf implements InputBuf {
        private final ByteBuf byteBuf;

        NettyInputBuf(ByteBuf byteBuf) {
            this.byteBuf = byteBuf;
        }

        @Override
        public InputStream inputStream() {
            return new ByteBufInputStream(byteBuf);
        }

        @Override
        public ByteBuffer nioByteBuffer() {
            return byteBuf.nioBuffer();
        }

        @Override
        public int size() {
            return byteBuf.readableBytes();
        }

        @Override
        public boolean hasMemoryAddress() {
            return byteBuf.hasMemoryAddress();
        }

        @Override
        public boolean release() {
            return byteBuf.release();
        }
    }

    enum State {
        MAGIC,
        SIGN,
        STATUS,
        ID,
        BODY_SIZE,
        BODY
    }
}
