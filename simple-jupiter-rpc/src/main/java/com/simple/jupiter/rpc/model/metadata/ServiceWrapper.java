package com.simple.jupiter.rpc.model.metadata;

import com.simple.jupiter.rpc.provider.ProviderInterceptor;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.Pair;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class ServiceWrapper implements Serializable {

    private static final long serialVersionUID = 6690575889849847348L;

    // 服务元数据
    private final ServiceMetadata metadata;

    private final Object serviceProvider;

    private final ProviderInterceptor[] interceptors;

    // key:     method name
    // value:   pair.first:  方法参数类型(用于根据JLS规则实现方法调用的静态分派)
    //          pair.second: 方法显式声明抛出的异常类型
    private final Map<String, List<Pair<Class<?>[], Class<?>[]>>> extensions;

    // 权重 hashCode() 与 equals() 不把weight计算在内
    private int weight = JConstants.DEFAULT_WEIGHT;
    // provider私有线程池
    private Executor executor;


}
