package com.simple.jupiter.transport.processor;

import com.simple.jupiter.transport.Status;
import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.payload.JRequestPayload;

public interface ProviderProcessor {

    /**
     * 处理正常请求
     */
    void handleRequest(JChannel channel, JRequestPayload request) throws Exception;

    /**
     * 处理异常
     */
    void handleException(JChannel channel, JRequestPayload request, Status status, Throwable cause);

    void shutdown();
}
