package com.simple.jupiter.rpc.load.balance;

import com.simple.jupiter.util.JServiceLoader;

public final class LoadBalancerFactory {

    public static LoadBalancer getInstance(LoadBalancerType type, String name) {
        if (type == LoadBalancerType.RANDOM) {
            return RandomLoadBalancer.instance();
        }

        if (type == LoadBalancerType.ROUND_ROBIN) {
            return RoundRobinLoadBalancer.instance();
        }

        if (type == LoadBalancerType.EXT_SPI) {
            return ExtSpiFactoryHolder.factory.getInstance(name);
        }

        // 如果不指定, 默认的负载均衡算法是加权随机
        return RandomLoadBalancer.instance();
    }

    private LoadBalancerFactory() {}

    static class ExtSpiFactoryHolder {
        static final ExtSpiLoadBalancerFactory factory = JServiceLoader.load(ExtSpiLoadBalancerFactory.class)
            .first();
    }
}
