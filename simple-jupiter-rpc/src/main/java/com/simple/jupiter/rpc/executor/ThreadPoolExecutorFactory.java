package com.simple.jupiter.rpc.executor;

import com.simple.jupiter.concurrent.RejectedTaskPolicyWithReport;
import com.simple.jupiter.util.SpiMetadata;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.Strings;
import com.simple.jupiter.util.SystemPropertyUtil;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

import java.lang.reflect.Constructor;
import java.util.concurrent.*;

@SpiMetadata(name = "threadPool", priority = 1)
public class ThreadPoolExecutorFactory extends AbstractExecutorFactory{

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ThreadPoolExecutorFactory.class);

    @Override
    public CloseableExecutor newExecutor(Target target, String name) {
        final ThreadPoolExecutor executor = new ThreadPoolExecutor(
                coreWorkers(target),
                maxWorkers(target),
                120L,
                TimeUnit.SECONDS,
                workQueue(target),
                threadFactory(name),
                createRejectedPolicy(target, name, new RejectedTaskPolicyWithReport(name, "jupiter")));

        return new CloseableExecutor() {

            @Override
            public void execute(Runnable task) {
                executor.execute(task);
            }

            @Override
            public void shutdown() {
                logger.warn("ThreadPoolExecutorFactory#{} shutdown.", executor);
                executor.shutdownNow();
            }
        };
    }

    private BlockingQueue<Runnable> workQueue(Target target) {
        BlockingQueue<Runnable> workQueue = null;
        WorkQueueType queueType = queueType(target, WorkQueueType.ARRAY_BLOCKING_QUEUE);
        int queueCapacity = queueCapacity(target);
        switch (queueType) {
            case LINKED_BLOCKING_QUEUE:
                workQueue = new LinkedBlockingQueue<>(queueCapacity);
                break;
            case ARRAY_BLOCKING_QUEUE:
                workQueue = new ArrayBlockingQueue<>(queueCapacity);
                break;
        }

        return workQueue;
    }

    private RejectedExecutionHandler createRejectedPolicy(Target target, String name, RejectedExecutionHandler defaultHandler) {
        RejectedExecutionHandler handler = null;
        String handlerClass = null;
        switch (target) {
            case CONSUMER:
                handlerClass = SystemPropertyUtil.get(CONSUMER_THREAD_POOL_REJECTED_HANDLER);
                break;
            case PROVIDER:
                handlerClass = SystemPropertyUtil.get(PROVIDER_THREAD_POOL_REJECTED_HANDLER);
                break;
        }
        if (Strings.isNotBlank(handlerClass)) {
            try {
                Class<?> cls = Class.forName(handlerClass);
                try {
                    Constructor<?> constructor = cls.getConstructor(String.class, String.class);
                    handler = (RejectedExecutionHandler) constructor.newInstance(name, "jupiter");
                } catch (NoSuchMethodException e) {
                    handler = (RejectedExecutionHandler) cls.newInstance();
                }
            } catch (Exception e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Construct {} failed, {}.", handlerClass, StackTraceUtil.stackTrace(e));
                }
            }
        }

        return handler == null ? defaultHandler : handler;
    }

    private WorkQueueType queueType(Target target, WorkQueueType defaultType) {
        WorkQueueType queueType = null;
        switch (target) {
            case CONSUMER:
                queueType = WorkQueueType.parse(SystemPropertyUtil.get(CONSUMER_EXECUTOR_QUEUE_TYPE));
                break;
            case PROVIDER:
                queueType = WorkQueueType.parse(SystemPropertyUtil.get(PROVIDER_EXECUTOR_QUEUE_TYPE));
                break;
        }

        return queueType == null ? defaultType : queueType;
    }

    enum WorkQueueType {
        LINKED_BLOCKING_QUEUE,
        ARRAY_BLOCKING_QUEUE;

        static WorkQueueType parse(String name) {
            for (WorkQueueType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }
}
