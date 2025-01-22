package com.simple.jupiter.rpc.consumer.invoker;

import java.util.List;
import com.simple.jupiter.rpc.consumer.dispatcher.Dispatcher;
import com.simple.jupiter.rpc.model.metadata.ClusterStrategyConfig;
import com.simple.jupiter.rpc.model.metadata.MethodSpecialConfig;
import com.simple.jupiter.rpc.model.metadata.ServiceMetadata;

public class SyncGenericInvoker extends AbstractInvoker implements GenericInvoker {

    public SyncGenericInvoker(String appName,
                              ServiceMetadata metadata,
                              Dispatcher dispatcher,
                              ClusterStrategyConfig defaultStrategy,
                              List<MethodSpecialConfig> methodSpecialConfigs) {
        super(appName, metadata, dispatcher, defaultStrategy, methodSpecialConfigs);
    }

    @Override
    public Object $invoke(String methodName, Object... args) throws Throwable {
        return doInvoke(methodName, args, Object.class, true);
    }
}
