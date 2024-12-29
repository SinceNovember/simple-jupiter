package com.simple.jupiter.rpc;

import com.simple.jupiter.rpc.model.metadata.MessageWrapper;
import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.transport.payload.JRequestPayload;

import java.util.Collections;
import java.util.Map;

public class JRequest {


    private final JRequestPayload payload;   // 请求bytes/stream
    private MessageWrapper message;          // 请求服务的对象包装

    public JRequest() {
        this(new JRequestPayload());
    }

    public JRequest(JRequestPayload payload) {
        this.payload = payload;
    }

    public JRequestPayload payload() {
        return payload;
    }

    public long invokeId() {
        return payload.invokeId();
    }

    public long timestamp() {
        return payload.timestamp();
    }

    public byte serializerCode() {
        return payload.serializerCode();
    }

    public void bytes(byte serializerCode, byte[] bytes) {
        payload.bytes(serializerCode, bytes);
    }

    public void outputBuf(byte serializerCode, OutputBuf outputBuf) {
        payload.outputBuf(serializerCode, outputBuf);
    }

    public MessageWrapper message() {
        return message;
    }

    public void message(MessageWrapper message) {
        this.message = message;
    }

    public Map<String, String> getAttachments() {
        Map<String, String> attachments =
                message != null ? message.getAttachments() : null;
        return attachments != null ? attachments : Collections.emptyMap();
    }

    public void putAttachment(String key, String value) {
        if (message != null) {
            message.putAttachment(key, value);
        }
    }

    @Override
    public String toString() {
        return "JRequest{" +
                "invokeId=" + invokeId() +
                ", timestamp=" + timestamp() +
                ", serializerCode=" + serializerCode() +
                ", message=" + message +
                '}';
    }
}
