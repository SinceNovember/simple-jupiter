/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simple.jupiter.registry;

import com.simple.jupiter.register.AbstractRegistryService;
import com.simple.jupiter.register.RegisterMeta;
import com.simple.jupiter.transport.JConnection;
import com.simple.jupiter.transport.UnresolvedAddress;
import com.simple.jupiter.transport.UnresolvedSocketAddress;
import com.simple.jupiter.util.Maps;
import com.simple.jupiter.util.Requires;
import com.simple.jupiter.util.SpiMetadata;
import com.simple.jupiter.util.Strings;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

import java.util.Collection;
import java.util.concurrent.ConcurrentMap;

/**
 * Default registry service.
 * 该类主要先跟所有的注册中心进行连接后，聚合处理对应的服务
 * jupiter
 * org.jupiter.registry.jupiter
 *
 * @author jiachun.fjc
 */
@SpiMetadata(name = "default")
public class DefaultRegistryService extends AbstractRegistryService {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultRegistryService.class);

    /**
     * 注册中心地址 -> 连接器
     */
    private final ConcurrentMap<UnresolvedAddress, DefaultRegistry> clients = Maps.newConcurrentMap();

    /**
     * 向所有注册中心 发送订阅信息 用于Client
     * @param serviceMeta
     */
    @Override
    protected void doSubscribe(RegisterMeta.ServiceMeta serviceMeta) {
        Collection<DefaultRegistry> allClients = clients.values();
        Requires.requireTrue(!allClients.isEmpty(), "init needed");

        logger.info("Subscribe: {}.", serviceMeta);

        for (DefaultRegistry c : allClients) {
            c.doSubscribe(serviceMeta);
        }
    }

    /**
     * 向所有注册中心 发送注册服务信息 用于Server
     */
    @Override
    protected void doRegister(RegisterMeta meta) {
        Collection<DefaultRegistry> allClients = clients.values();
        Requires.requireTrue(!allClients.isEmpty(), "init needed");

        logger.info("Register: {}.", meta);

        for (DefaultRegistry c : allClients) {
            c.doRegister(meta);
        }
        getRegisterMetaMap().put(meta, RegisterState.DONE);
    }

    /**
     * 向所有注册中心 发送取消注册服务信息 用于Server
     */
    @Override
    protected void doUnregister(RegisterMeta meta) {
        Collection<DefaultRegistry> allClients = clients.values();
        Requires.requireTrue(!allClients.isEmpty(), "init needed");

        logger.info("Unregister: {}.", meta);

        for (DefaultRegistry c : allClients) {
            c.doUnregister(meta);
        }
    }

    @Override
    protected void doCheckRegisterNodeStatus() {
        // the default registry service does nothing
    }

    /**
     * 向对应连接的注册中心进行连接，存到clients map中，用来做同一发送事件
     * @param connectString list of servers to connect to [host1:port1,host2:port2....]
     */
    @Override
    public void connectToRegistryServer(String connectString) {
        Requires.requireNotNull(connectString, "connectString");

        String[] array = Strings.split(connectString, ',');
        for (String s : array) {
            String[] addressStr = Strings.split(s, ':');
            String host = addressStr[0];
            int port = Integer.parseInt(addressStr[1]);
            UnresolvedAddress address = new UnresolvedSocketAddress(host, port);
            DefaultRegistry client = clients.get(address);
            if (client == null) {
                DefaultRegistry newClient = new DefaultRegistry(this);
                client = clients.putIfAbsent(address, newClient);
                if (client == null) {
                    client = newClient;
                    JConnection connection = client.connect(address);
                    client.connectionManager().manage(connection);
                } else {
                    newClient.shutdownGracefully();
                }
            }
        }
    }

    @Override
    public void destroy() {
        for (DefaultRegistry c : clients.values()) {
            c.shutdownGracefully();
        }
    }
}