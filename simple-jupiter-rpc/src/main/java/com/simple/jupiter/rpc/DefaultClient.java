package com.simple.jupiter.rpc;

import com.simple.jupiter.register.*;
import com.simple.jupiter.rpc.consumer.processor.DefaultConsumerProcessor;
import com.simple.jupiter.rpc.model.metadata.ServiceMetadata;
import com.simple.jupiter.transport.*;
import com.simple.jupiter.transport.channel.JChannelGroup;
import com.simple.jupiter.util.*;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultClient implements JClient{

    // 服务订阅(SPI)
    private final RegistryService registryService;
    private final String appName;
    private JConnector<JConnection> connector;

    private final ConcurrentMap<RegisterMeta.Address, CopyOnWriteArrayList<OfflineListener>> offlineListeners =
            Maps.newConcurrentMap();

    public DefaultClient() {
        this(JConstants.UNKNOWN_APP_NAME, RegistryService.RegistryType.DEFAULT);
    }

    public DefaultClient(RegistryService.RegistryType registryType) {
        this(JConstants.UNKNOWN_APP_NAME, registryType);
    }

    public DefaultClient(String appName) {
        this(appName, RegistryService.RegistryType.DEFAULT);
    }

    public DefaultClient(String appName, RegistryService.RegistryType registryType) {
        this.appName = Strings.isBlank(appName) ? JConstants.UNKNOWN_APP_NAME : appName;
        registryType = registryType == null ? RegistryService.RegistryType.DEFAULT : registryType;
        registryService = JServiceLoader.load(RegistryService.class).find(registryType.getValue());
    }

    @Override
    public String appName() {
        return appName;
    }

    @Override
    public JConnector<JConnection> connector() {
        return connector;
    }

    @Override
    public JClient withConnector(JConnector<JConnection> connector) {
        if (connector.processor() == null) {
            connector.withProcessor(new DefaultConsumerProcessor());
        }
        this.connector = connector;
        return this;
    }

    @Override
    public RegistryService registryService() {
        return registryService;
    }

    @Override
    public Collection<RegisterMeta> lookup(Directory directory) {
        RegisterMeta.ServiceMeta serviceMeta = toServiceMeta(directory);
        return registryService.lookup(serviceMeta);
    }

    @Override
    public JConnector.ConnectionWatcher watchConnections(Class<?> interfaceClass) {
        return watchConnections(interfaceClass, JConstants.DEFAULT_VERSION);
    }

    @Override
    public JConnector.ConnectionWatcher watchConnections(Class<?> interfaceClass, String version) {
        Requires.requireNotNull(interfaceClass, "interfaceClass");
        ServiceProvider annotation = interfaceClass.getAnnotation(ServiceProvider.class);
        Requires.requireNotNull(annotation, interfaceClass + " is not a ServiceProvider interface");
        String providerName = annotation.name();
        providerName = Strings.isNotBlank(providerName) ? providerName : interfaceClass.getName();
        version = Strings.isNotBlank(version) ? version : JConstants.DEFAULT_VERSION;

        return watchConnections(new ServiceMetadata(annotation.group(), providerName, version));
    }

    @Override
    public JConnector.ConnectionWatcher watchConnections(final Directory directory) {

        JConnector.ConnectionWatcher manager = new JConnector.ConnectionWatcher() {

            private final JConnectionManager connectionManager = connector.connectionManager();
            private final ReentrantLock lock = new ReentrantLock();
            private final Condition notifyCondition = lock.newCondition();
            private final AtomicBoolean signalNeeded = new AtomicBoolean(false);


            @Override
            public void start() {
                subscribe(directory, new NotifyListener() {
                    @Override
                    public void notify(RegisterMeta registerMeta, NotifyEvent event) {
                        UnresolvedAddress address = new UnresolvedSocketAddress(registerMeta.getHost(), registerMeta.getPort());
                        final JChannelGroup group = connector.group(address);
                        if (event == NotifyEvent.CHILD_ADDED) {
                            if (group.isAvailable()) {
                                onSucceed(group, signalNeeded.getAndSet(false));
                            } else {
                                if (group.isConnecting()) {
                                    group.onAvailable(() -> onSucceed(group, signalNeeded.getAndSet(false)));
                                } else {
                                    group.setConnecting(true);
                                    JConnection[] connections = connectTo(address, group, registerMeta, true);
                                    final AtomicInteger countdown = new AtomicInteger(connections.length);
                                    for (JConnection c : connections) {
                                        c.operationComplete(isSuccess -> {
                                            if (isSuccess) {
                                                onSucceed(group, signalNeeded.getAndSet(false));
                                            }
                                            if (countdown.decrementAndGet() <= 0) {
                                                group.setConnecting(false);
                                            }
                                        });
                                    }
                                }
                            }
                            group.putWeight(directory, registerMeta.getWeight());
                        } else if (event == NotifyEvent.CHILD_REMOVED) {
                            connector.removeChannelGroup(directory, group);
                            group.removeWeight(directory);
                            if (connector.directoryGroup().getRefCount(group) <= 0) {
                                connectionManager.cancelAutoReconnect(address);
                            }
                        }
                    }

                    private JConnection[] connectTo(UnresolvedAddress address, JChannelGroup group, RegisterMeta registerMeta, boolean async) {
                        int connCount = registerMeta.getConnCount();
                        connCount = Math.max(connCount, 1);

                        JConnection[] connections = new JConnection[connCount];
                        group.setCapacity(connCount);
                        for (int i = 0; i < connCount; i++) {
                            JConnection connection = connector.connect(address, async);
                            connections[i] = connection;
                            connectionManager.manage(connection);
                        }
                        offlineListening(address, () -> {
                            //取消这个地址下的所有的自动连接
                            connectionManager.cancelAutoReconnect(address);
                            if (!group.isAvailable()) {
                                connector.removeChannelGroup(directory, group);
                            }
                        });
                        return connections;
                    }

                    private void onSucceed(JChannelGroup group, boolean doSignal) {
                        connector.addChannelGroup(directory, group);

                        if (doSignal) {
                            final ReentrantLock _lock = lock;
                            _lock.lock();
                            try {
                                notifyCondition.signalAll();
                            } finally {
                                _lock.unlock();
                            }
                        }
                    }
                });
            }

            @Override
            public boolean waitForAvailable(long timeoutMillis) {
                //判断该服务下面是否有提供者(server)可用(客户端已经跟对应的提供者进行了连接)
                if (connector.isDirectoryAvailable(directory)) {
                    return true;
                }


                long remains = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

                boolean available = false;
                final ReentrantLock _look = lock;
                _look.lock();
                try {
                    signalNeeded.set(true);
                    // avoid "spurious wakeup" occurs
                    while (!(available = connector.isDirectoryAvailable(directory))) {
                        //如果上面的连接成功的话 说明该服务已经有可能的连接了，会唤醒这边
                        //超过超时时间会直接抛出InterruptedException
                        if ((remains = notifyCondition.awaitNanos(remains)) <= 0) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    ThrowUtil.throwException(e);
                } finally {
                    _look.unlock();
                }

                return available || connector.isDirectoryAvailable(directory);
            }
        };
        manager.start();

        return manager;
    }

    @Override
    public boolean awaitConnections(Class<?> interfaceClass, long timeoutMillis) {
        return awaitConnections(interfaceClass, JConstants.DEFAULT_VERSION, timeoutMillis);
    }

    @Override
    public boolean awaitConnections(Class<?> interfaceClass, String version, long timeoutMillis) {
        JConnector.ConnectionWatcher watcher = watchConnections(interfaceClass, version);
        return watcher.waitForAvailable(timeoutMillis);
    }

    @Override
    public boolean awaitConnections(Directory directory, long timeoutMillis) {
        JConnector.ConnectionWatcher watcher = watchConnections(directory);
        return watcher.waitForAvailable(timeoutMillis);
    }

    @Override
    public void subscribe(Directory directory, NotifyListener listener) {
        registryService.subscribe(toServiceMeta(directory), listener);
    }

    @Override
    public void offlineListening(UnresolvedAddress address, OfflineListener listener) {
        if (registryService instanceof AbstractRegistryService) {
            ((AbstractRegistryService) registryService).offlineListening(toAddress(address), listener);
        } else {
            throw new UnsupportedOperationException();
        }
    }


    @Override
    public void shutdownGracefully() {

    }

    @Override
    public void connectToRegistryServer(String connectString) {

    }

    private static RegisterMeta.ServiceMeta toServiceMeta(Directory directory) {
        RegisterMeta.ServiceMeta serviceMeta = new RegisterMeta.ServiceMeta();
        serviceMeta.setGroup(Requires.requireNotNull(directory.getGroup(), "group"));
        serviceMeta.setServiceProviderName(Requires.requireNotNull(directory.getServiceProviderName(), "serviceProviderName"));
        serviceMeta.setVersion(Requires.requireNotNull(directory.getVersion(), "version"));
        return serviceMeta;
    }

    private static RegisterMeta.Address toAddress(UnresolvedAddress address) {
        return new RegisterMeta.Address(address.getHost(), address.getPort());
    }
}
