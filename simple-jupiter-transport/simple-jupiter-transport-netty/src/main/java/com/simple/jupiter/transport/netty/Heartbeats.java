package com.simple.jupiter.transport.netty;

import com.simple.jupiter.transport.JProtocolHeader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class Heartbeats {

    private static final ByteBuf HEARTBEAT_BUF;

    static {
        ByteBuf buf = Unpooled.buffer(JProtocolHeader.HEADER_SIZE);
        buf.writeShort(JProtocolHeader.MAGIC);
        buf.writeByte(JProtocolHeader.HEARTBEAT); // 心跳包这里可忽略高地址的4位序列化/反序列化标志
        buf.writeByte(0);
        buf.writeLong(0);
        buf.writeInt(0);
        HEARTBEAT_BUF = Unpooled.unreleasableBuffer(buf).asReadOnly();
    }

    public static ByteBuf heartbeatContent() {
        return HEARTBEAT_BUF.duplicate();
    }
}
