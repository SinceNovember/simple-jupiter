package com.simple.jupiter.transport;

import java.util.Collection;
import com.simple.jupiter.transport.channel.CopyOnWriteGroupList;
import com.simple.jupiter.transport.channel.DirectoryJChannelGroup;
import com.simple.jupiter.transport.channel.JChannelGroup;
import com.simple.jupiter.transport.processor.ConsumerProcessor;

/**
 * 注意 JConnector 单例即可, 不要创建多个实例.
 *
 * jupiter
 * org.jupiter.transport
 *
 * @author jiachun.fjc
 */
public interface JConnector<C> extends Transporter {

    JConfig config();

    ConsumerProcessor processor();

    void withProcessor(ConsumerProcessor processor);

    C connect(UnresolvedAddress address);

    C connect(UnresolvedAddress address, boolean async);

    /**
     * 将该地址加入到channelGroup map中 返回加入后的group
     * @param address
     * @return
     */
    JChannelGroup group(UnresolvedAddress address);

    Collection<JChannelGroup> groups();

    boolean addChannelGroup(Directory directory, JChannelGroup group);

    boolean removeChannelGroup(Directory directory, JChannelGroup group);

    CopyOnWriteGroupList directory(Directory directory);

    boolean isDirectoryAvailable(Directory directory);

    /**
     * Returns the {@link DirectoryJChannelGroup}.
     */
    DirectoryJChannelGroup directoryGroup();

    JConnectionManager connectionManager();

    void shutdownGracefully();

    interface ConnectionWatcher {

        /**
         * Start to connect to server.
         */
        void start();

        /**
         * Wait until the connections is available or timeout,
         * if available return true, otherwise return false.
         */
        boolean waitForAvailable(long timeoutMillis);
    }

}
