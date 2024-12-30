package com.simple.jupiter.rpc.provider;

public interface ProviderInterceptor {

    /**
     * This code is executed before the method is optionally called.
     *
     * @param provider      provider be intercepted
     * @param methodName    name of the method to call
     * @param args          arguments to the method call
     */
    void beforeInvoke(Object provider, String methodName, Object[] args);

    /**
     * This code is executed after the method is optionally called.
     *
     * @param provider      provider be intercepted
     * @param methodName    name of the called method
     * @param args          arguments to the called method
     * @param result        result of the call, in the case of a call failure, {@code result}  is null
     * @param failCause     exception of the call, in the case of a call succeeds, {@code failCause}  is null
     */
    void afterInvoke(Object provider, String methodName, Object[] args, Object result, Throwable failCause);


}