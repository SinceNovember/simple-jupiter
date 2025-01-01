package com.simple.jupiter.rpc;

import com.simple.jupiter.register.RegistryService;
import com.simple.jupiter.rpc.flow.control.FlowController;
import com.simple.jupiter.rpc.model.metadata.ServiceWrapper;
import com.simple.jupiter.rpc.provider.ProviderInterceptor;
import com.simple.jupiter.transport.Directory;
import com.simple.jupiter.transport.JAcceptor;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;

public class DefaultServer implements JServer {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultServer.class);




    @Override
    public JAcceptor acceptor() {
        return null;
    }

    @Override
    public JServer withAcceptor(JAcceptor acceptor) {
        return null;
    }

    @Override
    public RegistryService registryService() {
        return null;
    }

    @Override
    public void withGlobalInterceptors(ProviderInterceptor... globalInterceptors) {

    }

    @Override
    public FlowController<JRequest> globalFlowController() {
        return null;
    }

    @Override
    public void withGlobalFlowController(FlowController<JRequest> flowController) {

    }

    @Override
    public ServiceRegistry serviceRegistry() {
        return null;
    }

    @Override
    public ServiceWrapper lookupService(Directory directory) {
        return null;
    }

    @Override
    public ServiceWrapper removeService(Directory directory) {
        return null;
    }

    @Override
    public List<ServiceWrapper> allRegisteredServices() {
        return List.of();
    }

    @Override
    public void publish(ServiceWrapper serviceWrapper) {

    }

    @Override
    public void publish(ServiceWrapper... serviceWrappers) {

    }

    @Override
    public <T> void publishWithInitializer(ServiceWrapper serviceWrapper, ProviderInitializer<T> initializer, Executor executor) {

    }

    @Override
    public void publishAll() {

    }

    @Override
    public void unpublish(ServiceWrapper serviceWrapper) {

    }

    @Override
    public void unpublishAll() {

    }

    @Override
    public void start() throws InterruptedException {

    }

    @Override
    public void start(boolean sync) throws InterruptedException {

    }

    @Override
    public void shutdownGracefully() {

    }

    @Override
    public void connectToRegistryServer(String connectString) {

    }

    interface ServiceProviderContainer {

        /**
         * 注册服务(注意并不是发布服务到注册中心, 只是注册到本地容器)
         */
        void registerService(String uniqueKey, ServiceWrapper serviceWrapper);

        /**
         * 本地容器查找服务
         */
        ServiceWrapper lookupService(String uniqueKey);

        /**
         * 从本地容器移除服务
         */
        ServiceWrapper removeService(String uniqueKey);

        /**
         * 获取本地容器中所有服务
         */
        List<ServiceWrapper> getAllServices();
    }
}
