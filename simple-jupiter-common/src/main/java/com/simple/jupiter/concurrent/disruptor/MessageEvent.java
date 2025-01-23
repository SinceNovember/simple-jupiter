package com.simple.jupiter.concurrent.disruptor;

public class MessageEvent<T> {

    private T message;

    public T getMessage() {
        return message;
    }

    public void setMessage(T message) {
        this.message = message;
    }
}
