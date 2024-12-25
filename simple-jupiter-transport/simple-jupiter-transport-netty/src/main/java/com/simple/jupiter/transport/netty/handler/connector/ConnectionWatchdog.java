package com.simple.jupiter.transport.netty.handler.connector;

import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import com.simple.jupiter.transport.channel.JChannelGroup;
import com.simple.jupiter.transport.netty.channel.NettyChannel;
import com.simple.jupiter.transport.netty.handler.ChannelHandlerHolder;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.util.Timeout;
import io.netty.util.Timer;
import io.netty.util.TimerTask;

@ChannelHandler.Sharable
public abstract class ConnectionWatchdog extends ChannelInboundHandlerAdapter implements TimerTask, ChannelHandlerHolder {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ConnectionWatchdog.class);

    private static final int ST_STARTED = 1;
    private static final int ST_STOPPED = 2;

    private final Bootstrap bootstrap;
    private final Timer timer;
    private final SocketAddress remoteAddress;
    private final JChannelGroup group;
    private volatile int state = ST_STARTED;
    private int attempts;

    public ConnectionWatchdog(Bootstrap bootstrap, Timer timer, SocketAddress remoteAddress, JChannelGroup group) {
        this.bootstrap = bootstrap;
        this.timer = timer;
        this.remoteAddress = remoteAddress;
        this.group = group;
    }


    public boolean isStarted() {
        return state == ST_STARTED;
    }

    public void start() {
        state = ST_STARTED;
    }

    public void stop() {
        state = ST_STOPPED;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        Channel ch = ctx.channel();

        if (group != null) {
            group.add(NettyChannel.attachChannel(ch));
        }

        attempts = 0;
        logger.info("Connects with {}.", ch);
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        boolean doReconnect = isReconnectNeeded();
        if (doReconnect) {
            if (attempts < 12) {
                attempts++;
            }
            long timeout = 2 << attempts;
            timer.newTimeout(this, timeout, TimeUnit.MICROSECONDS);
        }
        logger.warn("Disconnects with {}, address: {}, reconnect: {}.", ctx.channel(), remoteAddress, doReconnect);

        ctx.fireChannelInactive();
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        if (!isReconnectNeeded()) {
            logger.warn("Cancel reconnecting with {}.", remoteAddress);
            return;
        }

        ChannelFuture future;
        synchronized (bootstrap) {
            bootstrap.handler(new ChannelInitializer<Channel>() {

                @Override
                protected void initChannel(Channel ch) throws Exception {
                    ch.pipeline().addLast(handlers());
                }
            });
            future = bootstrap.connect(remoteAddress);
        }
        future.addListener((ChannelFutureListener) f -> {
            boolean succeed = f.isSuccess();

            logger.warn("Reconnects with {}, {}.", remoteAddress, succeed ? "succeed" : "failed");

            if (!succeed) {
                f.channel().pipeline().fireChannelInactive();
            }
        });

    }

    private boolean isReconnectNeeded() {
        return isStarted() && (group == null || (group.size() < group.getCapacity()));
    }
}
