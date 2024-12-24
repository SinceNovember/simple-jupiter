package com.simple.jupiter.transport.channel;

import java.util.EventListener;

public interface JFutureListener<C> extends EventListener {

    void operationSuccess(C c) throws Exception;

    void operationFailure(C c, Throwable cause) throws Exception;

}
