package com.simple.jupiter.transport;


import java.net.SocketAddress;
import com.simple.jupiter.transport.processor.ProviderProcessor;

/**
 * Server acceptor.
 *
 * 注意 JAcceptor 单例即可, 不要创建多个实例.
 *
 * jupiter
 * org.jupiter.transport
 *
 * @author jiachun.fjc
 */
public interface JAcceptor extends Transporter {

    /**
     * 本地的地址
     */
    SocketAddress localAddress();

    /**
     * 本地的端口
     * @return
     */
    int boundPort();

    /**
     * 配置serverChannel跟childChannel的optional
     * @return
     */
    JConfigGroup configGroup();

    /**
     * 服务端具体处理请求的逻辑
     * @return
     */
    ProviderProcessor processor();

    /**
     * 绑定请求处理器
     * @param processor
     */
    void withProcessor(ProviderProcessor processor);

    /**
     * Start the server and wait until the server socket is closed.
     */
    void start() throws InterruptedException;

    /**
     * Start the server.
     */
    void start(boolean sync) throws InterruptedException;

    /**
     * Shutdown the server gracefully.
     */
    void shutdownGracefully();

}
