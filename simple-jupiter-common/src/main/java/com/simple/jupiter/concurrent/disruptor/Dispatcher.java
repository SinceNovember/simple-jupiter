package com.simple.jupiter.concurrent.disruptor;

import com.simple.jupiter.util.JConstants;

public interface Dispatcher<T> {

    int BUFFER_SIZE = 32768;
    int MAX_NUM_WORKERS = JConstants.AVAILABLE_PROCESSORS << 3;


    boolean dispatch(T message);

    void shutdown();
}
