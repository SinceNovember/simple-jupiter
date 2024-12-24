package com.simple.jupiter.transport.netty.channel;


import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.transport.JProtocolHeader;
import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.channel.JFutureListener;
import com.simple.jupiter.transport.netty.alloc.AdaptiveOutputBufAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.internal.PlatformDependent;

import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.util.Queue;

/**
 * 对Netty Channel 的包装, 通过静态方法 attachChannel(Channel) 获取一个实例, NettyChannel 实例构造后会attach到对应 Channel 上, 不需要每次创建. jupiter org. jupiter. transport. netty. channel
 * Author:
 * jiachun. fjc
 */
public class NettyChannel implements JChannel {

    private static final AttributeKey<NettyChannel> NETTY_CHANNEL_KEY = AttributeKey.valueOf("netty.channel");

    public static NettyChannel attachChannel(Channel channel) {
        Attribute<NettyChannel> attr = channel.attr(NETTY_CHANNEL_KEY);
        NettyChannel nChannel = attr.get();

        if (nChannel == null) {
            NettyChannel newNChannel = new NettyChannel(channel);
            nChannel = attr.setIfAbsent(newNChannel);
            if (nChannel == null) {
                nChannel = newNChannel;
            }
        }
        return nChannel;
    }

    private final Channel channel;

    private final AdaptiveOutputBufAllocator.Handle allocHandle = AdaptiveOutputBufAllocator.DEFAULT.newHandle();

    private final Queue<Runnable> taskQueue = PlatformDependent.newMpscQueue(1024);
    private final Runnable runAllTasks = this::runAllTasks;

    private NettyChannel(Channel channel) {
        this.channel = channel;
    }

    public Channel channel() {
        return channel;
    }

    @Override
    public String id() {
        return channel.id().asShortText();
    }

    @Override
    public boolean isActive() {
        return channel.isActive();
    }

    @Override
    public boolean inIoThread() {
        return channel.eventLoop().inEventLoop();
    }

    @Override
    public SocketAddress localAddress() {
        return channel.localAddress();
    }

    @Override
    public SocketAddress remoteAddress() {
        return channel.remoteAddress();
    }

    @Override
    public boolean isWritable() {
        return channel.isWritable();
    }

    @Override
    public boolean isMarkedReconnect() {
        ConnectionWatchdog watchdog = channel.pipeline().get(ConnectionWatchdog.class);
        return watchdog != null && watchdog.isStarted();
    }

    @Override
    public boolean isAutoRead() {
        return channel.config().isAutoRead();
    }

    @Override
    public void setAutoRead(boolean autoRead) {
        channel.config().setAutoRead(autoRead);
    }

    @Override
    public JChannel close() {
        channel.close();
        return this;
    }

    @Override
    public JChannel close(JFutureListener<JChannel> listener) {
        final JChannel jChannel = this;
        channel.close().addListener(future -> {
            if (future.isSuccess()) {
                listener.operationSuccess(jChannel);
            } else {
                listener.operationFailure(jChannel, future.cause());
            }
        });
        return jChannel;
    }

    @Override
    public JChannel write(Object msg) {
        channel.writeAndFlush(msg, channel.voidPromise());
        return this;
    }

    @Override
    public JChannel write(Object msg, final JFutureListener<JChannel> listener) {
        final JChannel jChannel = this;
        channel.writeAndFlush(msg)
                .addListener((ChannelFutureListener) future -> {
                    if (future.isSuccess()) {
                        listener.operationSuccess(jChannel);
                    } else {
                        listener.operationFailure(jChannel, future.cause());
                    }
                });
        return jChannel;
    }

    @Override
    public void addTask(Runnable task) {
        EventLoop eventLoop = channel.eventLoop();

        while (!taskQueue.offer(task)) {
            if (eventLoop.inEventLoop()) {
                runAllTasks.run();
            } else {
                // TODO await?
                eventLoop.execute(runAllTasks);
            }
        }

        if (!taskQueue.isEmpty()) {
            eventLoop.execute(runAllTasks);
        }
    }

    private void runAllTasks() {
        if (taskQueue.isEmpty()) {
            return;
        }

        for (; ; ) {
            Runnable task = taskQueue.poll();
            if (task == null) {
                return;
            }
            task.run();
        }
    }

    @Override
    public OutputBuf allocOutputBuf() {
        return new NettyOutputBuf(allocHandle, channel.alloc());
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || (obj instanceof NettyChannel && channel.equals(((NettyChannel) obj).channel));
    }

    @Override
    public int hashCode() {
        return channel.hashCode();
    }

    @Override
    public String toString() {
        return channel.toString();
    }

    static final class NettyOutputBuf implements OutputBuf {

        private final AdaptiveOutputBufAllocator.Handle allocHandle;
        private final ByteBuf byteBuf;
        private ByteBuffer nioByteBuffer;

        public NettyOutputBuf(AdaptiveOutputBufAllocator.Handle allocHandle, ByteBufAllocator alloc) {
            this.allocHandle = allocHandle;
            byteBuf = allocHandle.allocate(alloc);

            byteBuf.ensureWritable(JProtocolHeader.HEADER_SIZE)
                    // reserved 16-byte protocol header location
                    .writerIndex(byteBuf.writerIndex() + JProtocolHeader.HEADER_SIZE);
        }

        @Override
        public OutputStream outputStream() {
            return new ByteBufOutputStream(byteBuf); // should not be called more than once
        }

        @Override
        public ByteBuffer nioByteBuffer(int minWritableBytes) {
            if (minWritableBytes < 0) {
                minWritableBytes = byteBuf.writableBytes();
            }

            if (nioByteBuffer == null) {
                nioByteBuffer = newNioByteBuffer(byteBuf, minWritableBytes);
            }

            if (nioByteBuffer.remaining() >= minWritableBytes) {
                return nioByteBuffer;
            }

            int position = nioByteBuffer.position();
            nioByteBuffer = newNioByteBuffer(byteBuf, position + minWritableBytes);
            nioByteBuffer.position(position);
            return nioByteBuffer;
        }

        @Override
        public int size() {
            if (nioByteBuffer == null) {
                return byteBuf.readableBytes();
            }
            return Math.max(byteBuf.readableBytes(), nioByteBuffer.position());
        }

        @Override
        public boolean hasMemoryAddress() {
            return byteBuf.hasMemoryAddress();
        }

        @Override
        public Object backingObject() {
            int actualWroteBytes = byteBuf.writerIndex();
            if (nioByteBuffer != null) {
                actualWroteBytes += nioByteBuffer.position();
            }

            allocHandle.record(actualWroteBytes);

            return byteBuf.writerIndex(actualWroteBytes);
        }

        private static ByteBuffer newNioByteBuffer(ByteBuf byteBuf, int writableBytes) {
            return byteBuf
                    .ensureWritable(writableBytes)
                    .nioBuffer(byteBuf.writerIndex(), byteBuf.writableBytes());
        }
    }

}
