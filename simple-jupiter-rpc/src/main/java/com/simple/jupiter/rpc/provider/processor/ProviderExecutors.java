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
package com.simple.jupiter.rpc.provider.processor;


import com.simple.jupiter.rpc.executor.CloseableExecutor;
import com.simple.jupiter.rpc.executor.ExecutorFactory;
import com.simple.jupiter.rpc.executor.ThreadPoolExecutorFactory;
import com.simple.jupiter.util.JServiceLoader;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.SystemPropertyUtil;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

/**
 * jupiter
 * org.jupiter.rpc.provider.processor
 *
 * @author jiachun.fjc
 */
public class ProviderExecutors {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(ProviderExecutors.class);

    private static final CloseableExecutor executor;

    static {
        String factoryName = SystemPropertyUtil.get("jupiter.executor.factory.provider.factory_name", "threadPool");
        ExecutorFactory factory;
        try {
            factory = (ExecutorFactory) JServiceLoader.load(ProviderExecutorFactory.class)
                    .find(factoryName);
        } catch (Throwable t) {
            logger.warn("Failed to load provider's executor factory [{}], cause: {}, " +
                    "[ThreadPoolExecutorFactory] will be used as default.", factoryName, StackTraceUtil.stackTrace(t));

            factory = new ThreadPoolExecutorFactory();
        }

        executor = factory.newExecutor(ExecutorFactory.Target.PROVIDER, "jupiter-provider-processor");
    }

    public static CloseableExecutor executor() {
        return executor;
    }
}
