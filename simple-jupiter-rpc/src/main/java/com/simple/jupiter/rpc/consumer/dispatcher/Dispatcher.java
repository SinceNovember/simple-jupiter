package com.simple.jupiter.rpc.consumer.dispatcher;

import java.util.List;
import com.simple.jupiter.rpc.JRequest;
import com.simple.jupiter.rpc.consumer.ConsumerInterceptor;
import com.simple.jupiter.rpc.consumer.future.InvokeFuture;
import com.simple.jupiter.rpc.model.metadata.MethodSpecialConfig;

public interface Dispatcher {

    <T> InvokeFuture<T> dispatch(JRequest request, Class<T> returnType);

    Dispatcher interceptors(List<ConsumerInterceptor> interceptors);

    Dispatcher timeoutMillis(long timeoutMillis);

    Dispatcher methodSpecialConfigs(List<MethodSpecialConfig> methodSpecialConfigs);

}
