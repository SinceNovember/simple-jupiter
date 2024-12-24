package com.simple.jupiter.transport.processor;

import com.simple.jupiter.transport.channel.JChannel;
import com.simple.jupiter.transport.payload.JResponsePayload;

public interface ConsumerProcessor {

    void handleResponse(JChannel channel, JResponsePayload response) throws Exception;

    void shutdown();
}
