package com.simple.jupiter.rpc.executor;

public interface CloseableExecutor {

    void execute(Runnable task);

    void shutdown();
}
