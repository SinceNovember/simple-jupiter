package com.simple.jupiter.transport.netty;


import com.simple.jupiter.transport.CodecConfig;
import com.simple.jupiter.transport.JConfig;
import com.simple.jupiter.transport.JOption;
import com.simple.jupiter.transport.netty.handler.*;
import com.simple.jupiter.transport.netty.handler.acceptor.AcceptorHandler;
import com.simple.jupiter.transport.netty.handler.acceptor.AcceptorIdleStateTrigger;
import com.simple.jupiter.transport.processor.ProviderProcessor;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.Requires;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.flush.FlushConsolidationHandler;

import java.net.SocketAddress;

/**
 * Jupiter tcp acceptor based on netty.
 *
 * <pre>
 * *********************************************************************
 *            I/O Request                       I/O Response
 *                 │                                 △
 *                                                   │
 *                 │
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┼ ─ ─ ─ ─ ─ ─ ─ ─
 * │               │                                                  │
 *                                                   │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐       ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐   │
 *     IdleStateChecker#inBound          IdleStateChecker#outBound
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘       └ ─ ─ ─ ─ ─ ─△─ ─ ─ ─ ─ ─ ┘   │
 *                 │                                 │
 * │                                                                  │
 *                 │                                 │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐                                     │
 *     AcceptorIdleStateTrigger                      │
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘                                     │
 *                 │                                 │
 * │                                                                  │
 *                 │                                 │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐       ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐   │
 *          ProtocolDecoder                   ProtocolEncoder
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘       └ ─ ─ ─ ─ ─ ─△─ ─ ─ ─ ─ ─ ┘   │
 *                 │                                 │
 * │                                                                  │
 *                 │                                 │
 * │  ┌ ─ ─ ─ ─ ─ ─▽─ ─ ─ ─ ─ ─ ┐                                     │
 *          AcceptorHandler                          │
 * │  └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘                                     │
 *                 │                                 │
 * │                    ┌ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┐                     │
 *                 ▽                                 │
 * │               ─ ─ ▷│       Processor       ├ ─ ─▷                │
 *
 * │                    └ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ┘                     │
 * ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─
 * </pre>
 *
 * jupiter
 * org.jupiter.transport.netty
 *
 * @author jiachun.fjc
 */
public class JNettyTcpAcceptor extends NettyTcpAcceptor{

    public static final int DEFAULT_ACCEPTOR_PORT = 18090;
    // handlers
    private final AcceptorIdleStateTrigger idleStateTrigger = new AcceptorIdleStateTrigger();

    private final ChannelOutboundHandler encoder =
            CodecConfig.isCodecLowCopy() ? new LowCopyProtocolEncoder() : new ProtocolEncoder();

    private final AcceptorHandler handler = new AcceptorHandler();

    public JNettyTcpAcceptor() {
        super(DEFAULT_ACCEPTOR_PORT);
    }

    public JNettyTcpAcceptor(int port) {
        super(port);
    }

    public JNettyTcpAcceptor(SocketAddress localAddress) {
        super(localAddress);
    }

    public JNettyTcpAcceptor(int port, int nWorkers) {
        super(port, nWorkers);
    }

    public JNettyTcpAcceptor(SocketAddress localAddress, int nWorkers) {
        super(localAddress, nWorkers);
    }

    public JNettyTcpAcceptor(int port, boolean isNative) {
        super(port, isNative);
    }

    public JNettyTcpAcceptor(SocketAddress localAddress, boolean isNative) {
        super(localAddress, isNative);
    }

    public JNettyTcpAcceptor(int port, int nWorkers, boolean isNative) {
        super(port, nWorkers, isNative);
    }

    public JNettyTcpAcceptor(SocketAddress localAddress, int nWorkers, boolean isNative) {
        super(localAddress, nWorkers, isNative);
    }

    @Override
    protected void init() {
        super.init();

        // parent options
        JConfig parent = configGroup().parent();
        parent.setOption(JOption.SO_BACKLOG, 32768);
        parent.setOption(JOption.SO_REUSEADDR, true);

        // child options
        JConfig child = configGroup().child();
        child.setOption(JOption.SO_REUSEADDR, true);
    }

    @Override
    protected ChannelFuture bind(SocketAddress localAddress) {
        ServerBootstrap boot = bootstrap();

        initChannelFactory();

        boot.childHandler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(
                        new FlushConsolidationHandler(JConstants.EXPLICIT_FLUSH_AFTER_FLUSHES, true),
                        new IdleStateChecker(timer, JConstants.READER_IDLE_TIME_SECONDS, 0, 0),
                        idleStateTrigger,
                        CodecConfig.isCodecLowCopy() ? new LowCopyProtocolDecoder() : new ProtocolDecoder(),
                        encoder,
                        handler);
            }
        });

        setOptions();

        return boot.bind(localAddress);
    }

    @Override
    protected void setProcessor(ProviderProcessor processor) {
        handler.processor(Requires.requireNotNull(processor, "processor"));
    }


}
