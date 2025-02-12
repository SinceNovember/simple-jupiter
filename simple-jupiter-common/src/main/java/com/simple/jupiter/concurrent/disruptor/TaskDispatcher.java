package com.simple.jupiter.concurrent.disruptor;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.EventFactory;
import com.lmax.disruptor.InsufficientCapacityException;
import com.lmax.disruptor.LiteBlockingWaitStrategy;
import com.lmax.disruptor.LiteTimeoutBlockingWaitStrategy;
import com.lmax.disruptor.PhasedBackoffWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.TimeoutBlockingWaitStrategy;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.YieldingWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.simple.jupiter.concurrent.NamedThreadFactory;
import com.simple.jupiter.concurrent.RejectedTaskPolicyWithReport;
import com.simple.jupiter.util.Pow2;
import com.simple.jupiter.util.Requires;
import org.jetbrains.annotations.NotNull;

public class TaskDispatcher implements Dispatcher<Runnable>, Executor {

    private static final EventFactory<MessageEvent<Runnable>> eventFactory = MessageEvent::new;

    private final Disruptor<MessageEvent<Runnable>> disruptor;

    private final ExecutorService reserveExecutor;

    public TaskDispatcher(int numWorkers, ThreadFactory threadFactory) {
        this(numWorkers, threadFactory, BUFFER_SIZE, 0, WaitStrategyType.BLOCKING_WAIT, null);
    }

    public TaskDispatcher(int numWorkers,
                          ThreadFactory threadFactory,
                          int bufSize,
                          int numReserveWorkers,
                          WaitStrategyType waitStrategyType,
                          String dumpPrefixName) {

        Requires.requireTrue(bufSize > 0, "bufSize must be larger than 0");
        if (!Pow2.isPowerOfTwo(bufSize)) {
            bufSize = Pow2.roundToPowerOfTwo(bufSize);
        }

        if (numReserveWorkers > 0) {
            String name = "reserve.processor";

            RejectedExecutionHandler handler;
            if (dumpPrefixName == null) {
                handler = new RejectedTaskPolicyWithReport(name);
            } else {
                handler = new RejectedTaskPolicyWithReport(name, dumpPrefixName);
            }

            reserveExecutor = new ThreadPoolExecutor(
                0,
                numReserveWorkers,
                60L,
                TimeUnit.SECONDS,
                new SynchronousQueue<>(),
                new NamedThreadFactory(name),
                handler);
        } else {
            reserveExecutor = null;
        }

        WaitStrategy waitStrategy;
        switch (waitStrategyType) {
            case BLOCKING_WAIT:
                waitStrategy = new BlockingWaitStrategy();
                break;
            case LITE_BLOCKING_WAIT:
                waitStrategy = new LiteBlockingWaitStrategy();
                break;
            case TIMEOUT_BLOCKING_WAIT:
                waitStrategy = new TimeoutBlockingWaitStrategy(1000, TimeUnit.MILLISECONDS);
                break;
            case LITE_TIMEOUT_BLOCKING_WAIT:
                waitStrategy = new LiteTimeoutBlockingWaitStrategy(1000, TimeUnit.MILLISECONDS);
                break;
            case PHASED_BACK_OFF_WAIT:
                waitStrategy = PhasedBackoffWaitStrategy.withLiteLock(1000, 1000, TimeUnit.NANOSECONDS);
                break;
            case SLEEPING_WAIT:
                waitStrategy = new SleepingWaitStrategy();
                break;
            case YIELDING_WAIT:
                waitStrategy = new YieldingWaitStrategy();
                break;
            case BUSY_SPIN_WAIT:
                waitStrategy = new BusySpinWaitStrategy();
                break;
            default:
                throw new UnsupportedOperationException(waitStrategyType.toString());
        }

        if (threadFactory == null) {
            threadFactory = new NamedThreadFactory("disruptor.processor");
        }

        Disruptor<MessageEvent<Runnable>> dr =
            new Disruptor<>(eventFactory, bufSize, threadFactory, ProducerType.MULTI, waitStrategy);
        dr.setDefaultExceptionHandler(new LoggingExceptionHandler());
        numWorkers = Math.min(Math.abs(numWorkers), MAX_NUM_WORKERS);
        if (numWorkers == 1) {
            dr.handleEventsWith(new TaskHandler());
        } else {
            TaskHandler[] handlers = new TaskHandler[numWorkers];
            for (int i = 0; i < numWorkers; i++) {
                handlers[i] = new TaskHandler();
            }
            dr.handleEventsWithWorkerPool(handlers);
        }

        dr.start();
        disruptor = dr;
    }

    @Override
    public boolean dispatch(Runnable message) {
        RingBuffer<MessageEvent<Runnable>> ringBuffer = disruptor.getRingBuffer();
        try {
            long sequence = ringBuffer.tryNext();
            try {
                MessageEvent<Runnable> event = ringBuffer.get(sequence);
                event.setMessage(message);
            } finally {
                ringBuffer.publish(sequence);
            }
            return true;
        } catch (InsufficientCapacityException e) {
            // 这个异常是Disruptor当做全局goto使用的, 是单例的并且没有堆栈信息, 不必担心抛出异常的性能问题
            return false;
        }
    }

    @Override
    public void shutdown() {
        disruptor.shutdown();
        if (reserveExecutor != null) {
            reserveExecutor.shutdownNow();
        }
    }

    @Override
    public void execute(Runnable message) {
        if (!dispatch(message)) {
            // 备选线程池
            if (reserveExecutor != null) {
                reserveExecutor.execute(message);
            } else {
                throw new RejectedExecutionException("Ring buffer is full");
            }
        }
    }

}
