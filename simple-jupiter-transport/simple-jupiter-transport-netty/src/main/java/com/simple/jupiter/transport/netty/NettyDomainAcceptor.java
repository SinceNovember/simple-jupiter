package com.simple.jupiter.transport.netty;

import java.util.concurrent.ThreadFactory;
import com.simple.jupiter.transport.JConfigGroup;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.unix.DomainSocketAddress;

public abstract class NettyDomainAcceptor extends NettyAcceptor {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NettyDomainAcceptor.class);

    private final NettyConfig.NettyDomainConfigGroup configGroup = new NettyConfig.NettyDomainConfigGroup();

    public NettyDomainAcceptor(DomainSocketAddress domainAddress) {
        super(Protocol.DOMAIN, domainAddress);
        init();
    }

    public NettyDomainAcceptor(DomainSocketAddress domainAddress, int nWorkers) {
        super(Protocol.DOMAIN, domainAddress, nWorkers);
        init();
    }

    public NettyDomainAcceptor(DomainSocketAddress domainAddress, int nBosses, int nWorkers) {
        super(Protocol.DOMAIN, domainAddress, nBosses, nWorkers);
        init();
    }

    @Override
    protected void setOptions() {
        super.setOptions();

        ServerBootstrap boot = bootstrap();

        // child options
        NettyConfig.NettyDomainConfigGroup.ChildConfig child = configGroup.child();

        WriteBufferWaterMark waterMark =
            createWriteBufferWaterMark(child.getWriteBufferLowWaterMark(), child.getWriteBufferHighWaterMark());

        boot.childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, waterMark);
    }

    @Override
    public JConfigGroup configGroup() {
        return configGroup;
    }

    @Override
    public void start() throws InterruptedException {
        start(true);
    }

    @Override
    public void start(boolean sync) throws InterruptedException {
        // wait until the server socket is bind succeed.
        ChannelFuture future = bind(localAddress).sync();

        if (logger.isInfoEnabled()) {
            logger.info("Jupiter unix domain server start" + (sync ? ", and waits until the server socket closed." : ".")
                + JConstants.NEWLINE + " {}.", toString());
        }

        if (sync) {
            // wait until the server socket is closed.
            future.channel().closeFuture().sync();
        }
    }

    @Override
    public void setIoRatio(int bossIoRatio, int workerIoRatio) {
        EventLoopGroup boss = boss();
        if (boss instanceof EpollEventLoopGroup) {
            ((EpollEventLoopGroup) boss).setIoRatio(bossIoRatio);
        } else if (boss instanceof KQueueEventLoopGroup) {
            ((KQueueEventLoopGroup) boss).setIoRatio(bossIoRatio);
        }

        EventLoopGroup worker = worker();
        if (worker instanceof EpollEventLoopGroup) {
            ((EpollEventLoopGroup) worker).setIoRatio(workerIoRatio);
        } else if (worker instanceof KQueueEventLoopGroup) {
            ((KQueueEventLoopGroup) worker).setIoRatio(workerIoRatio);
        }
    }

    @Override
    protected EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory) {
        SocketChannelProvider.SocketType socketType = socketType();
        switch (socketType) {
            case NATIVE_EPOLL_DOMAIN:
                return new EpollEventLoopGroup(nThreads, tFactory);
            case NATIVE_KQUEUE_DOMAIN:
                return new KQueueEventLoopGroup(nThreads, tFactory);
            default:
                throw new IllegalStateException("Invalid socket type: " + socketType);
        }
    }

    protected void initChannelFactory() {
        SocketChannelProvider.SocketType socketType = socketType();
        switch (socketType) {
            case NATIVE_EPOLL_DOMAIN:
                bootstrap().channelFactory(SocketChannelProvider.NATIVE_EPOLL_DOMAIN_ACCEPTOR);
                break;
            case NATIVE_KQUEUE_DOMAIN:
                bootstrap().channelFactory(SocketChannelProvider.NATIVE_KQUEUE_DOMAIN_ACCEPTOR);
                break;
            default:
                throw new IllegalStateException("Invalid socket type: " + socketType);
        }
    }

    protected SocketChannelProvider.SocketType socketType() {
        if (NativeSupport.isNativeEPollAvailable()) {
            // netty provides the unix domain  socket transport for Linux using JNI.
            return SocketChannelProvider.SocketType.NATIVE_EPOLL_DOMAIN;
        }
        if (NativeSupport.isNativeKQueueAvailable()) {
            // netty provides the unix domain  socket transport for BSD systems such as MacOS using JNI.
            return SocketChannelProvider.SocketType.NATIVE_KQUEUE_DOMAIN;
        }
        throw new UnsupportedOperationException("Unsupported unix domain socket");
    }




}
