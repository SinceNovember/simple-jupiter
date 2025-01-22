/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simple.jupiter.rpc.consumer.invoker;

import java.lang.reflect.Method;
import java.util.List;
import com.simple.jupiter.rpc.consumer.dispatcher.Dispatcher;
import com.simple.jupiter.rpc.consumer.future.InvokeFuture;
import com.simple.jupiter.rpc.consumer.future.InvokeFutureContext;
import com.simple.jupiter.rpc.model.metadata.ClusterStrategyConfig;
import com.simple.jupiter.rpc.model.metadata.MethodSpecialConfig;
import com.simple.jupiter.rpc.model.metadata.ServiceMetadata;
import com.simple.jupiter.util.Reflects;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;


/**
 * Asynchronous call, {@link #invoke(Method, Object[])}
 * returns a default value of the corresponding method.
 *
 * 异步调用.
 *
 * jupiter
 * org.jupiter.rpc.consumer.invoker
 *
 * @author jiachun.fjc
 */
public class AsyncInvoker extends AbstractInvoker {

    public AsyncInvoker(String appName,
                        ServiceMetadata metadata,
                        Dispatcher dispatcher,
                        ClusterStrategyConfig defaultStrategy,
                        List<MethodSpecialConfig> methodSpecialConfigs) {
        super(appName, metadata, dispatcher, defaultStrategy, methodSpecialConfigs);
    }

    @RuntimeType
    public Object invoke(@Origin Method method, @AllArguments @RuntimeType Object[] args) throws Throwable {
        Class<?> returnType = method.getReturnType();

        Object result = doInvoke(method.getName(), args, returnType, false);

        InvokeFutureContext.set((InvokeFuture<?>) result);

        return Reflects.getTypeDefaultValue(returnType);
    }
}
