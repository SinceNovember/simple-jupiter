package com.simple.jupiter.transport.channel;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import com.simple.jupiter.transport.Directory;
import com.simple.jupiter.util.Maps;

public class DirectoryJChannelGroup {

    // key: 服务标识(group_serviceProviderName_version); value: 提供服务的节点列表(group list)
    private final ConcurrentMap<String, CopyOnWriteGroupList> groups = Maps.newConcurrentMap();
    // 对应服务节点(group)的引用计数
    private final GroupRefCounterMap groupRefCounter = new GroupRefCounterMap();

    public CopyOnWriteGroupList find(Directory directory) {
        String _directory = directory.directoryString();
        CopyOnWriteGroupList groupList = groups.get(_directory);
        if (groupList == null) {
            CopyOnWriteGroupList newGroupList = new CopyOnWriteGroupList(this);
            groupList = groups.putIfAbsent(_directory, newGroupList);
            if (groupList == null) {
                groupList = newGroupList;
            }
        }
        return groupList;
    }

    /**
     * 获取指定group的引用计数
     */
    public int getRefCount(JChannelGroup group) {
        AtomicInteger counter = groupRefCounter.get(group);
        if (counter == null) {
            return 0;
        }
        return counter.get();
    }

    /**
     * 指定group的引用计数 +1
     */
    public int incrementRefCount(JChannelGroup group) {
        return groupRefCounter.getOrCreate(group).incrementAndGet();
    }

    /**
     * 指定group的引用计数 -1, 如果引用计数为 0 则remove
     */
    public int decrementRefCount(JChannelGroup group) {
        AtomicInteger counter = groupRefCounter.get(group);
        if (counter == null) {
            return 0;
        }
        int count = counter.decrementAndGet();
        if (count == 0) {
            // get与remove并不是原子操作, 但在当前场景是可接受的
            groupRefCounter.remove(group);
        }
        return count;
    }

    static class GroupRefCounterMap extends ConcurrentHashMap<JChannelGroup, AtomicInteger> {

        private static final long serialVersionUID = 6590976614405397299L;

        public AtomicInteger getOrCreate(JChannelGroup key) {
            AtomicInteger counter = super.get(key);
            if (counter == null) {
                AtomicInteger newCounter = new AtomicInteger(0);
                counter = super.putIfAbsent(key, newCounter);
                if (counter == null) {
                    counter = newCounter;
                }
            }
            return counter;
        }
    }
}
