package com.simple.jupiter.transport.netty;

import com.simple.jupiter.concurrent.NamedThreadFactory;
import com.simple.jupiter.transport.*;
import com.simple.jupiter.transport.channel.DirectoryJChannelGroup;
import com.simple.jupiter.transport.channel.JChannelGroup;
import com.simple.jupiter.transport.netty.channel.NettyChannelGroup;
import com.simple.jupiter.transport.processor.ConsumerProcessor;
import com.simple.jupiter.util.ClassUtil;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.Maps;
import com.simple.jupiter.util.Requires;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;
import jdk.internal.event.Event;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadFactory;

public abstract class NettyConnector implements JConnector<JConnection> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(NettyConnector.class);

    static {
        // touch off DefaultChannelId.<clinit>
        // because getProcessId() sometimes too slow
        ClassUtil.initializeClass("io.netty.channel.DefaultChannelId", 500);
    }

    protected final Protocol protocol;
    protected final HashedWheelTimer timer = new HashedWheelTimer(new NamedThreadFactory("connector.timer", true));

    /**
     * address:port -> 对应服务端的所有channel 封装到group里，因为客户端每调用一次connect都会生成一个新的channel
     */
    private final ConcurrentMap<UnresolvedAddress, JChannelGroup> addressGroups = Maps.newConcurrentMap();
    private final DirectoryJChannelGroup directoryGroup = new DirectoryJChannelGroup();
    private final JConnectionManager connectionManager = new JConnectionManager();

    private Bootstrap bootstrap;
    private EventLoopGroup worker;
    private int nWorkers;

    private ConsumerProcessor processor;

    public NettyConnector(Protocol protocol) {
        this(protocol, JConstants.AVAILABLE_PROCESSORS << 1);
    }

    public NettyConnector(Protocol protocol, int nWorkers) {
        this.protocol = protocol;
        this.nWorkers = nWorkers;
    }

    protected void init() {
        ThreadFactory workerFactory = workerThreadFactory("jupiter.connector");
        worker = initEventLoopGroup(nWorkers, workerFactory);
        bootstrap = new Bootstrap().group(worker);

        JConfig child = config();
        child.setOption(JOption.IO_RATIO, 100);

        doInit();
    }

    protected abstract void doInit();

    protected ThreadFactory workerThreadFactory(String name) {
        return new DefaultThreadFactory(name, Thread.MAX_PRIORITY);
    }

    @Override
    public Protocol protocol() {
        return protocol;
    }

    @Override
    public ConsumerProcessor processor() {
        return processor;
    }

    @Override
    public void withProcessor(ConsumerProcessor processor) {
        setProcessor(this.processor = processor);
    }

    @Override
    public JChannelGroup group(UnresolvedAddress address) {
        Requires.requireNotNull(address, "address");

        JChannelGroup group = addressGroups.get(address);
        if (group == null) {

        }

    }


    protected JChannelGroup channelGroup(UnresolvedAddress address) {
        return new NettyChannelGroup(address);
    }



}
