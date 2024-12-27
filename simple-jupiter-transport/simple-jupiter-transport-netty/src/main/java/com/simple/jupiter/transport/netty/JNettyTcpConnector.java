package com.simple.jupiter.transport.netty;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.TimeUnit;
import com.simple.jupiter.transport.CodecConfig;
import com.simple.jupiter.transport.JConnection;
import com.simple.jupiter.transport.JOption;
import com.simple.jupiter.transport.UnresolvedAddress;
import com.simple.jupiter.transport.channel.JChannelGroup;
import com.simple.jupiter.transport.exception.ConnectFailedException;
import com.simple.jupiter.transport.netty.handler.IdleStateChecker;
import com.simple.jupiter.transport.netty.handler.LowCopyProtocolDecoder;
import com.simple.jupiter.transport.netty.handler.LowCopyProtocolEncoder;
import com.simple.jupiter.transport.netty.handler.ProtocolDecoder;
import com.simple.jupiter.transport.netty.handler.ProtocolEncoder;
import com.simple.jupiter.transport.netty.handler.connector.ConnectionWatchdog;
import com.simple.jupiter.transport.netty.handler.connector.ConnectorHandler;
import com.simple.jupiter.transport.netty.handler.connector.ConnectorIdleStateTrigger;
import com.simple.jupiter.transport.processor.ConsumerProcessor;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.Requires;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;
import io.netty.handler.flush.FlushConsolidationHandler;

public class JNettyTcpConnector extends NettyTcpConnector {

    private final ConnectorIdleStateTrigger idleStateTrigger = new ConnectorIdleStateTrigger();

    private final ChannelOutboundHandler encoder =
        CodecConfig.isCodecLowCopy() ? new LowCopyProtocolEncoder() : new ProtocolEncoder();

    private final ConnectorHandler handler = new ConnectorHandler();

    public JNettyTcpConnector() {
        super();
    }

    public JNettyTcpConnector(boolean isNative) {
        super(isNative);
    }

    public JNettyTcpConnector(int nWorkers) {
        super(nWorkers);
    }

    public JNettyTcpConnector(int nWorkers, boolean isNative) {
        super(nWorkers, isNative);
    }


    @Override
    protected void doInit() {
        // child options
        config().setOption(JOption.SO_REUSEADDR, true);
        config().setOption(JOption.CONNECT_TIMEOUT_MILLIS, (int) TimeUnit.SECONDS.toMillis(3));
        // channel factory
        initChannelFactory();
    }

    @Override
    public JConnection connect(UnresolvedAddress address, boolean async) {
        setOptions();

        final Bootstrap boot = bootstrap();
        final SocketAddress socketAddress = InetSocketAddress.createUnresolved(address.getHost(), address.getPort());
        final JChannelGroup group = group(address);

        // 重连watchdog
        final ConnectionWatchdog watchdog = new ConnectionWatchdog(boot, timer, socketAddress, group) {

            @Override
            public ChannelHandler[] handlers() {
                return new ChannelHandler[] {
                    new FlushConsolidationHandler(JConstants.EXPLICIT_FLUSH_AFTER_FLUSHES, true),
                    this,
                    new IdleStateChecker(timer, 0, JConstants.WRITER_IDLE_TIME_SECONDS, 0),
                    idleStateTrigger,
                    CodecConfig.isCodecLowCopy() ? new LowCopyProtocolDecoder() : new ProtocolDecoder(),
                    encoder,
                    handler
                };
            }
        };

        ChannelFuture future;
        try {
            synchronized (bootstrapLock()) {
                boot.handler(new ChannelInitializer<Channel>() {

                    @Override
                    protected void initChannel(Channel ch) throws Exception {
                        ch.pipeline().addLast(watchdog.handlers());
                    }
                });

                future = boot.connect(socketAddress);
            }

            // 以下代码在synchronized同步块外面是安全的
            if (!async) {
                future.sync();
            }
        } catch (Throwable t) {
            throw new ConnectFailedException("Connects to [" + address + "] fails", t);
        }


        return new JNettyConnection(address, future) {
            @Override
            public void setReconnect(boolean reconnect) {
                if (reconnect) {
                    watchdog.start();
                } else {
                    watchdog.stop();
                }
            }
        };
    }

    @Override
    protected void setProcessor(ConsumerProcessor processor) {
        handler.processor(Requires.requireNotNull(processor, "processor"));
    }
}
