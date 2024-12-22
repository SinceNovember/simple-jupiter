package com.simple.jupiter.transport;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.simple.jupiter.util.Maps;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

/**
 * Jupiter的连接管理器, 用于自动管理(按照地址归组)连接.
 *
 * jupiter
 * org.jupiter.transport
 *
 * @author jiachun.fjc
 */
public class JConnectionManager {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(JConnectionManager.class);

    private final ConcurrentMap<UnresolvedAddress, CopyOnWriteArrayList<JConnection>> connections = Maps.newConcurrentMap();

    public void manage(JConnection connection) {
        UnresolvedAddress address = connection.getAddress();
        CopyOnWriteArrayList<JConnection> list = connections.get(address);
        if (list == null) {
            CopyOnWriteArrayList<JConnection> newList = new CopyOnWriteArrayList<>();
            list = connections.putIfAbsent(address, newList);
            if (list == null) {
                list = newList;
            }
        }
        list.add(connection);
    }


    /**
     * 取消对指定地址的自动重连
     */
    public void cancelAutoReconnect(UnresolvedAddress address) {
        CopyOnWriteArrayList<JConnection> list = connections.remove(address);
        if (list != null) {
            for (JConnection c : list) {
                c.setReconnect(false);
            }
            logger.warn("Cancel reconnect to: {}.", address);
        }
    }

    /**
     * 取消对所有地址的自动重连
     */
    public void cancelAllAutoReconnect() {
        for (UnresolvedAddress address : connections.keySet()) {
            cancelAutoReconnect(address);
        }
    }

}
