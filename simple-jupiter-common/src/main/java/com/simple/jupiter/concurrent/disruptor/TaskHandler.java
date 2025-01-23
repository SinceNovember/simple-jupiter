package com.simple.jupiter.concurrent.disruptor;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;
import com.lmax.disruptor.TimeoutHandler;
import com.lmax.disruptor.WorkHandler;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

public class TaskHandler implements EventHandler<MessageEvent<Runnable>>, WorkHandler<MessageEvent<Runnable>>,
    TimeoutHandler, LifecycleAware {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(TaskHandler.class);

    @Override
    public void onTimeout(long sequence) throws Exception {
        if (logger.isWarnEnabled()) {
            logger.warn("Task timeout on: {}, sequence: {}.", Thread.currentThread().getName(), sequence);
        }
    }

    @Override
    public void onStart() {
        if (logger.isWarnEnabled()) {
            logger.warn("Task handler on start: {}.", Thread.currentThread().getName());
        }
    }

    @Override
    public void onShutdown() {
        if (logger.isWarnEnabled()) {
            logger.warn("Task handler on shutdown: {}.", Thread.currentThread().getName());
        }
    }

    @Override
    public void onEvent(MessageEvent<Runnable> event) throws Exception {
        event.getMessage().run();
    }

    @Override
    public void onEvent(MessageEvent<Runnable> event, long l, boolean b) throws Exception {
        event.getMessage().run();
    }
}
