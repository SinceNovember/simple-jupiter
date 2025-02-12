package com.simple.jupiter.rpc.consumer.invoker;

import java.util.List;
import com.simple.jupiter.rpc.DefaultFilterChain;
import com.simple.jupiter.rpc.JFilter;
import com.simple.jupiter.rpc.JFilterChain;
import com.simple.jupiter.rpc.JFilterContext;
import com.simple.jupiter.rpc.JFilterLoader;
import com.simple.jupiter.rpc.JRequest;
import com.simple.jupiter.rpc.consumer.cluster.ClusterInvoker;
import com.simple.jupiter.rpc.consumer.dispatcher.Dispatcher;
import com.simple.jupiter.rpc.consumer.future.InvokeFuture;
import com.simple.jupiter.rpc.model.metadata.ClusterStrategyConfig;
import com.simple.jupiter.rpc.model.metadata.MessageWrapper;
import com.simple.jupiter.rpc.model.metadata.MethodSpecialConfig;
import com.simple.jupiter.rpc.model.metadata.ServiceMetadata;

public abstract class AbstractInvoker {

    private final String appName;
    private final ServiceMetadata metadata; // 目标服务元信息

    private final ClusterStrategyBridging clusterStrategyBridging;

    public AbstractInvoker(String appName,
                           ServiceMetadata metadata,
                           Dispatcher dispatcher,
                           ClusterStrategyConfig defaultStrategy,
                           List<MethodSpecialConfig> methodSpecialConfigs) {
        this.appName = appName;
        this.metadata = metadata;
        clusterStrategyBridging = new ClusterStrategyBridging(dispatcher, defaultStrategy, methodSpecialConfigs);
    }

    protected Object doInvoke(String methodName, Object[] args, Class<?> returnType, boolean sync) throws Throwable {
        JRequest request = createRequest(methodName, args);
        ClusterInvoker invoker = clusterStrategyBridging.findClusterInvoker(methodName);

        Context invokeCtx = new Context(invoker, returnType, sync);
        Chains.invoke(request, invokeCtx);

        return invokeCtx.getResult();
    }

    private JRequest createRequest(String methodName, Object[] args) {
        MessageWrapper message = new MessageWrapper(metadata);
        message.setAppName(appName);
        message.setMethodName(methodName);
        // 不需要方法参数类型, 服务端会根据args具体类型按照JLS规则动态dispatch
        message.setArgs(args);

        JRequest request = new JRequest();
        request.message(message);

        return request;
    }

    static class Context implements JFilterContext {

        private final ClusterInvoker invoker;
        private final Class<?> returnType;
        private final boolean sync;

        private Object result;

        Context(ClusterInvoker invoker, Class<?> returnType, boolean sync) {
            this.invoker = invoker;
            this.returnType = returnType;
            this.sync = sync;
        }

        @Override
        public JFilter.Type getType() {
            return JFilter.Type.CONSUMER;
        }

        public ClusterInvoker getInvoker() {
            return invoker;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public boolean isSync() {
            return sync;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }
    }

    static class ClusterInvokeFilter implements JFilter {

        @Override
        public Type getType() {
            return JFilter.Type.CONSUMER;
        }

        @Override
        public <T extends JFilterContext> void doFilter(JRequest request, T filterCtx, JFilterChain next) throws Throwable {
            Context invokeCtx = (Context) filterCtx;
            ClusterInvoker invoker = invokeCtx.getInvoker();
            Class<?> returnType = invokeCtx.getReturnType();
            // invoke
            InvokeFuture<?> future = invoker.invoke(request, returnType);

            if (invokeCtx.isSync()) {
                invokeCtx.setResult(future.getResult());
            } else {
                invokeCtx.setResult(future);
            }
        }
    }

    static class Chains {

        private static final JFilterChain headChain;

        static {
            JFilterChain invokeChain = new DefaultFilterChain(new ClusterInvokeFilter(), null);
            headChain = JFilterLoader.loadExtFilters(invokeChain, JFilter.Type.CONSUMER);
        }

        static <T extends JFilterContext> T invoke(JRequest request, T invokeCtx) throws Throwable {
            headChain.doFilter(request, invokeCtx);
            return invokeCtx;
        }
    }

}
