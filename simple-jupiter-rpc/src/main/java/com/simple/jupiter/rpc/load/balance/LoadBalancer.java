package com.simple.jupiter.rpc.load.balance;

import com.simple.jupiter.transport.Directory;
import com.simple.jupiter.transport.channel.CopyOnWriteGroupList;
import com.simple.jupiter.transport.channel.JChannelGroup;

public interface LoadBalancer {
    /**
     * Select one in elements list.
     *
     * @param groups    elements for select
     * @param directory service directory
     */
    JChannelGroup select(CopyOnWriteGroupList groups, Directory directory);
}
