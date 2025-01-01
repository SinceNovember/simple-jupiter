package com.simple.jupiter.rpc.flow.control;

/**
 * 服务限流, 限流规则在服务端执行, 这可能会有一点点性能开销.
 *
 * 1. 每个 {@link org.jupiter.rpc.JServer} 都可设置一个App级别的全局限流器;
 * 2. 每个Provide也可以设置更细粒度的Provider级别限流器.
 *
 * jupiter
 * org.jupiter.rpc.flow.control
 *
 * @author jiachun.fjc
 */
public interface FlowController<T> {

    ControlResult flowControl(T t);
}
