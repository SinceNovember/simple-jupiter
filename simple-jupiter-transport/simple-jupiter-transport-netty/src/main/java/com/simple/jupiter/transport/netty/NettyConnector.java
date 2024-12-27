package com.simple.jupiter.transport.netty;

import com.simple.jupiter.concurrent.NamedThreadFactory;
import com.simple.jupiter.transport.*;
import com.simple.jupiter.transport.channel.CopyOnWriteGroupList;
import com.simple.jupiter.transport.channel.DirectoryJChannelGroup;
import com.simple.jupiter.transport.channel.JChannelGroup;
import com.simple.jupiter.transport.netty.channel.NettyChannelGroup;
import com.simple.jupiter.transport.netty.estimator.JMessageSizeEstimator;
import com.simple.jupiter.transport.processor.ConsumerProcessor;
import com.simple.jupiter.util.ClassUtil;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.Maps;
import com.simple.jupiter.util.Requires;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.WriteBufferWaterMark;
import io.netty.util.HashedWheelTimer;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.Collection;
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
    /**
     * : 服务标识 -> 提供服务的服务端channelGroup列表
     */
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
            JChannelGroup newGroup = channelGroup(address);
            group = addressGroups.putIfAbsent(address, newGroup);
            if (group == null) {
                group = newGroup;
            }
        }
        return group;
    }

    @Override
    public Collection<JChannelGroup> groups() {
        return addressGroups.values();
    }

    @Override
    public boolean addChannelGroup(Directory directory, JChannelGroup group) {
        CopyOnWriteGroupList groups = directory(directory);
        boolean added = groups.addIfAbsent(group);
        if (added) {
            if (logger.isInfoEnabled()) {
                logger.info("Added channel group: {} to {}.", group, directory.directoryString());
            }
        }
        return added;
    }
    @Override
    public boolean removeChannelGroup(Directory directory, JChannelGroup group) {
        CopyOnWriteGroupList groups = directory(directory);
        boolean removed = groups.remove(group);
        if (removed) {
            if (logger.isWarnEnabled()) {
                logger.warn("Removed channel group: {} in directory: {}.", group, directory.directoryString());
            }
        }
        return removed;
    }

    @Override
    public CopyOnWriteGroupList directory(Directory directory) {
        return directoryGroup.find(directory);
    }

    @Override
    public boolean isDirectoryAvailable(Directory directory) {
        CopyOnWriteGroupList groups = directory(directory);
        JChannelGroup[] snapshot = groups.getSnapshot();
        for (JChannelGroup g : snapshot) {
            if (g.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public DirectoryJChannelGroup directoryGroup() {
        return directoryGroup;
    }

    @Override
    public JConnectionManager connectionManager() {
        return connectionManager;
    }

    @Override
    public void shutdownGracefully() {
        connectionManager.cancelAllAutoReconnect();
        worker.shutdownGracefully().syncUninterruptibly();
        timer.stop();
        if (processor != null) {
            processor.shutdown();
        }
    }

    protected void setOptions() {
        JConfig child = config();

        setIoRatio(child.getOption(JOption.IO_RATIO));

        bootstrap.option(ChannelOption.MESSAGE_SIZE_ESTIMATOR, JMessageSizeEstimator.DEFAULT);
    }


    /**
     * A {@link Bootstrap} that makes it easy to bootstrap a {@link io.netty.channel.Channel} to use
     * for clients.
     */
    protected Bootstrap bootstrap() {
        return bootstrap;
    }

    /**
     * Lock object with bootstrap.
     */
    protected Object bootstrapLock() {
        return bootstrap;
    }

    /**
     * The {@link EventLoopGroup} for the child. These {@link EventLoopGroup}'s are used to handle
     * all the events and IO for {@link io.netty.channel.Channel}'s.
     */
    protected EventLoopGroup worker() {
        return worker;
    }

    protected JChannelGroup channelGroup(UnresolvedAddress address) {
        return new NettyChannelGroup(address);
    }

    protected void setProcessor(ConsumerProcessor processor) {
        // the default implementation does nothing
    }

    protected WriteBufferWaterMark createWriteBufferWaterMark(int bufLowWaterMark, int bufHighWaterMark) {
        WriteBufferWaterMark waterMark;
        if (bufLowWaterMark >= 0 && bufHighWaterMark > 0) {
            waterMark = new WriteBufferWaterMark(bufLowWaterMark, bufHighWaterMark);
        } else {
            waterMark = new WriteBufferWaterMark(512 * 1024, 1024 * 1024);
        }
        return waterMark;
    }

    /**
     * Sets the percentage of the desired amount of time spent for I/O in the child event loops.
     * The default value is {@code 50}, which means the event loop will try to spend the same
     * amount of time for I/O as for non-I/O tasks.
     */
    public abstract void setIoRatio(int workerIoRatio);

    /**
     * Create a new instance using the specified number of threads, the given {@link ThreadFactory}.
     */
    protected abstract EventLoopGroup initEventLoopGroup(int nThreads, ThreadFactory tFactory);


}
