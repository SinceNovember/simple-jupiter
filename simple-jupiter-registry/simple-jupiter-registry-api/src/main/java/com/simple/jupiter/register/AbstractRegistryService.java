package com.simple.jupiter.register;

import com.simple.jupiter.concurrent.NamedThreadFactory;
import com.simple.jupiter.concurrent.collection.ConcurrentSet;
import com.simple.jupiter.util.Lists;
import com.simple.jupiter.util.Maps;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.StampedLock;

public abstract class AbstractRegistryService implements RegistryService {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(AbstractRegistryService.class);

    private final LinkedBlockingQueue<RegisterMeta> queue = new LinkedBlockingQueue<>();
    private final ExecutorService registerExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("register.executor", true));
    private final ScheduledExecutorService registerScheduledExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("register.schedule.executor", true));
    private final ExecutorService localRegisterWatchExecutor = Executors.newSingleThreadExecutor(new NamedThreadFactory("local.register.watch.executor", true));

    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    /*
    一个服务对应多个提供端
     */
    private final ConcurrentMap<RegisterMeta.ServiceMeta, RegisterValue> registries =
            Maps.newConcurrentMap();

    private final ConcurrentMap<RegisterMeta.ServiceMeta, CopyOnWriteArrayList<NotifyListener>> subscribeListeners =
            Maps.newConcurrentMap();
    private final ConcurrentMap<RegisterMeta.Address, CopyOnWriteArrayList<OfflineListener>> offlineListeners =
            Maps.newConcurrentMap();

    // Consumer已订阅的信息
    private final ConcurrentSet<RegisterMeta.ServiceMeta> subscribeSet = new ConcurrentSet<>();
    // Provider已发布的注册信息
    private final ConcurrentMap<RegisterMeta, RegisterState> registerMetaMap = Maps.newConcurrentMap();

    public AbstractRegistryService() {
        //启动一个线程从队列中阻塞获取需要注册的服务，并给子类进行具体处理
        registerExecutor.execute(() -> {
            while (!shutdown.get()) {
                RegisterMeta meta = null;
                try {
                    meta = queue.take();
                    registerMetaMap.put(meta, RegisterState.PREPARE);
                    doRegister(meta);
                } catch (InterruptedException e) {
                    logger.warn("[register.executor] interrupted.");
                } catch (Throwable t) {
                    logger.error("Register [{}] fail: {}, will try again...", meta.getServiceMeta(), StackTraceUtil.stackTrace(t));

                    // 间隔一段时间再重新入队, 让出cpu
                    final RegisterMeta finalMeta = meta;
                    registerScheduledExecutor.schedule(() -> {
                        queue.add(finalMeta);
                    }, 1, TimeUnit.SECONDS);
                }
            }
        });

        localRegisterWatchExecutor.execute(() -> {
            while (!shutdown.get()) {
                try {
                    Thread.sleep(3000);
                    doCheckRegisterNodeStatus();
                } catch (InterruptedException e) {
                    logger.warn("[local.register.watch.executor] interrupted.");
                } catch (Throwable t) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Check register node status fail: {}, will try again...", StackTraceUtil.stackTrace(t));
                    }
                }
            }
        });
    }

    @Override
    public void register(RegisterMeta meta) {
        queue.add(meta);
    }

    @Override
    public void unregister(RegisterMeta meta) {
        if (!queue.remove(meta)) {
            registerMetaMap.remove(meta);
            doUnregister(meta);
        }
    }

    @Override
    public void subscribe(RegisterMeta.ServiceMeta serviceMeta, NotifyListener listener) {
        CopyOnWriteArrayList<NotifyListener> listeners = subscribeListeners.get(serviceMeta);
        if (listeners == null) {
            CopyOnWriteArrayList<NotifyListener> newListeners = new CopyOnWriteArrayList<>();
            listeners = subscribeListeners.putIfAbsent(serviceMeta, newListeners);
            if (listeners == null) {
                listeners = newListeners;
            }
        }
        listeners.add(listener);

        subscribeSet.add(serviceMeta);
        doSubscribe(serviceMeta);
    }

    @Override
    public Collection<RegisterMeta> lookup(RegisterMeta.ServiceMeta serviceMeta) {
        RegisterValue value = registries.get(serviceMeta);

        if (value == null) {
            return Collections.emptyList();
        }

        // do not try optimistic read
        final StampedLock stampedLock = value.lock;
        final long stamp = stampedLock.readLock();
        try {
            return Lists.newArrayList(value.metaSet);
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    @Override
    public Map<RegisterMeta.ServiceMeta, Integer> consumers() {
        Map<RegisterMeta.ServiceMeta, Integer> result = Maps.newHashMap();
        for (Map.Entry<RegisterMeta.ServiceMeta, RegisterValue> entry : registries.entrySet()) {
            RegisterValue value = entry.getValue();
            final StampedLock stampedLock = value.lock;
            long stamp = stampedLock.tryOptimisticRead();
            int optimisticVal = value.metaSet.size();
            if (stampedLock.validate(stamp)) {
                result.put(entry.getKey(), optimisticVal);
                continue;
            }
            stamp = stampedLock.readLock();
            try {
                result.put(entry.getKey(), value.metaSet.size());
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }
        return result;
    }

    @Override
    public Map<RegisterMeta, RegisterState> providers() {
        return new HashMap<>(registerMetaMap);
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public void shutdownGracefully() {
        if (!shutdown.getAndSet(true)) {
            try {
                registerExecutor.shutdownNow();
                registerScheduledExecutor.shutdownNow();
                localRegisterWatchExecutor.shutdownNow();
            } catch (Exception e) {
                logger.error("Failed to shutdown: {}.", StackTraceUtil.stackTrace(e));
            } finally {
                destroy();
            }
        }
    }

    public abstract void destroy();

    public void offlineListening(RegisterMeta.Address address, OfflineListener listener) {
        CopyOnWriteArrayList<OfflineListener> listeners = offlineListeners.get(address);
        if (listeners == null) {
            CopyOnWriteArrayList<OfflineListener> newListeners = new CopyOnWriteArrayList<>();
            listeners = offlineListeners.putIfAbsent(address, newListeners);
            if (listeners == null) {
                listeners = newListeners;
            }
        }
        listeners.add(listener);
    }

    public void offline(RegisterMeta.Address address) {
        // remove & notify
        CopyOnWriteArrayList<OfflineListener> listeners = offlineListeners.remove(address);
        if (listeners != null) {
            for (OfflineListener l : listeners) {
                l.offline();
            }
        }
    }

    /**
     *  通知新增或删除服务
     *  serviceMeta: 需要增加或删除的服务
     *  event: 增加或者删除类型
     *  array: 增加或删除哪些服务地址
      */
    public void notify(
            RegisterMeta.ServiceMeta serviceMeta, NotifyListener.NotifyEvent event, long version, RegisterMeta... array) {
        if (array == null || array.length == 0) {
            return;
        }

        RegisterValue value = registries.get(serviceMeta);
        if (value == null) {
            RegisterValue newValue = new RegisterValue();
            value = registries.putIfAbsent(serviceMeta, newValue);
            if (value == null) {
                value = newValue;
            }
        }

        boolean notifyNeeded = false;

        // segment-lock
        final StampedLock stampedLock = value.lock;
        final long stamp = stampedLock.writeLock();
        try {
            long lastVersion = value.version;
            if (version > lastVersion
                || (version < 0 && lastVersion > 0)) {
                if (event == NotifyListener.NotifyEvent.CHILD_REMOVED) {
                    for (RegisterMeta m : array) {
                        value.metaSet.remove(m);
                    }
                } else if (event == NotifyListener.NotifyEvent.CHILD_ADDED) {
                    Collections.addAll(value.metaSet, array);
                }
                value.version = version;
                notifyNeeded = true;
            }
        } finally {
            stampedLock.unlockWrite(stamp);
        }

        if (notifyNeeded) {
            CopyOnWriteArrayList<NotifyListener> listeners = subscribeListeners.get(serviceMeta);
            if (listeners != null) {
                for (NotifyListener l : listeners) {
                    for (RegisterMeta m : array) {
                        l.notify(m, event);
                    }
                }
            }
        }

    }

    protected abstract void doSubscribe(RegisterMeta.ServiceMeta serviceMeta);

    protected abstract void doRegister(RegisterMeta meta);

    protected abstract void doUnregister(RegisterMeta meta);

    protected abstract void doCheckRegisterNodeStatus();

    public ConcurrentSet<RegisterMeta.ServiceMeta> getSubscribeSet() {
        return subscribeSet;
    }

    public ConcurrentMap<RegisterMeta, RegisterState> getRegisterMetaMap() {
        return registerMetaMap;
    }

    protected static class RegisterValue {
        private long version = Long.MIN_VALUE;
        private final Set<RegisterMeta> metaSet = new HashSet<>();
        private final StampedLock lock = new StampedLock(); // segment-lock
    }
}
