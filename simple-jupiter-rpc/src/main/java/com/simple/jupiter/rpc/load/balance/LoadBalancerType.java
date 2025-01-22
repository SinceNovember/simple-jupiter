package com.simple.jupiter.rpc.load.balance;

public enum LoadBalancerType {

    ROUND_ROBIN,                // 加权轮询
    RANDOM,                     // 加权随机
    EXT_SPI;                    // 用户自行扩展, SPI方式加载

    public static LoadBalancerType parse(String name) {
        for (LoadBalancerType s : values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public static LoadBalancerType getDefault() {
        return RANDOM;
    }
}
