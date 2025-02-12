package com.simple.jupiter.monitor;


import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import com.simple.jupiter.monitor.handler.CommandHandler;
import com.simple.jupiter.monitor.handler.LsHandler;
import com.simple.jupiter.monitor.handler.RegistryHandler;
import com.simple.jupiter.register.RegistryMonitor;
import com.simple.jupiter.register.RegistryService;
import com.simple.jupiter.rpc.JClient;
import com.simple.jupiter.rpc.JServer;
import com.simple.jupiter.transport.netty.NettyTcpAcceptor;
import com.simple.jupiter.util.JConstants;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.Strings;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.ReferenceCountUtil;

/**
 * 监控服务, RegistryServer与ProviderServer都应该启用
 *
 * <pre>
 * 常用的monitor command说明:
 * ---------------------------------------------------------------------------------------------------------------------
 * help                                 // 帮助信息
 *
 * auth jupiter                         // 登录(默认密码为jupiter,
 *                                      // 可通过System.setProperty("monitor.server.password", "password")设置密码
 *
 * metrics -report                      // 输出当前节点所有指标度量信息
 * ls                                   // 本地查询发布和订阅的服务
 *
 * registry -address -p                 // 输出所有provider地址
 * registry -address -s                 // 输出所有consumer地址
 * registry -by_service                 // 根据服务(group providerServiceName version)查找所有提供当前服务的机器地址列表
 * registry -by_address                 // 根据地址(host port)查找该地址对用provider提供的所有服务
 *
 * metrics/registry ... -grep xxx       // 过滤metrics/registry的输出内容
 *
 * quit                                 // 退出
 * ---------------------------------------------------------------------------------------------------------------------
 * </pre>
 *
 * jupiter
 * org.jupiter.monitor
 *
 * @author jiachun.fjc
 */
public class MonitorServer extends NettyTcpAcceptor {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(MonitorServer.class);

    private static final int DEFAULT_PORT = 19999;

    // handlers
    private final TelnetHandler handler = new TelnetHandler();
    private final StringEncoder encoder = new StringEncoder(StandardCharsets.UTF_8);

    private volatile RegistryMonitor registryMonitor;
    private volatile JServer jupiterServer;
    private volatile JClient jupiterClient;

    public MonitorServer() {
        this(DEFAULT_PORT);
    }

    public MonitorServer(int port) {
        super(port, 1, false);
    }

    @Override
    protected ChannelFuture bind(SocketAddress localAddress) {
        ServerBootstrap boot = bootstrap();
        initChannelFactory();

        boot.childHandler(new ChannelInitializer<Channel>() {

            @Override
            protected void initChannel(Channel ch) throws Exception {
                ch.pipeline().addLast(
                    new DelimiterBasedFrameDecoder(8192, Delimiters.lineDelimiter()),
                    new StringDecoder(StandardCharsets.UTF_8),
                    encoder,
                    handler);
            }
        });

        setOptions();

        return boot.bind(localAddress);
    }

    @Override
    public void start() throws InterruptedException {
        super.start(false);
    }

    /**
     * For jupiter-registry-default
     */
    public void setRegistryMonitor(RegistryMonitor registryMonitor) {
        this.registryMonitor = registryMonitor;
    }

    public void setJupiterServer(JServer jupiterServer) {
        this.jupiterServer = jupiterServer;
    }

    public void setJupiterClient(JClient jupiterClient) {
        this.jupiterClient = jupiterClient;
    }

    @ChannelHandler.Sharable
    class TelnetHandler extends ChannelInboundHandlerAdapter {

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            Channel ch = ctx.channel();

            if (msg instanceof String) {
                String[] args = Strings.split(((String) msg).replace("\r\n", ""), ' ');
                if (args == null || args.length == 0) {
                    return;
                }

                Command command = Command.parse(args[0]);
                if (command == null) {
                    ch.writeAndFlush("invalid command!" + JConstants.NEWLINE);
                    return;
                }

                CommandHandler handler = command.handler();
                if (handler instanceof RegistryHandler) {
                    if (((RegistryHandler) handler).getRegistryMonitor() != registryMonitor) {
                        ((RegistryHandler) handler).setRegistryMonitor(registryMonitor);
                    }
                }
                if (handler instanceof LsHandler) {
                    RegistryService serverRegisterService = jupiterServer == null ? null
                        : jupiterServer.registryService();
                    if (((LsHandler) handler).getServerRegisterService() != serverRegisterService) {
                        ((LsHandler) handler).setServerRegisterService(serverRegisterService);
                    }
                    RegistryService clientRegisterService = jupiterClient == null ? null
                        : jupiterClient.registryService();
                    if (((LsHandler) handler).getClientRegisterService() != clientRegisterService) {
                        ((LsHandler) handler).setClientRegisterService(clientRegisterService);
                    }
                }
                handler.handle(ch, command, args);
            } else {
                logger.warn("Unexpected message type received: {}, channel: {}.", msg.getClass(), ch);

                ReferenceCountUtil.release(msg);
            }
        }

        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            ctx.writeAndFlush(JConstants.NEWLINE + "Welcome to jupiter monitor! Please auth with password." + JConstants.NEWLINE);
            Command command = Command.parse("help");
            CommandHandler handler = command.handler();
            handler.handle(ctx.channel(), command);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
            logger.error("An exception was caught: {}, channel {}.", StackTraceUtil.stackTrace(cause), ctx.channel());

            ctx.close();
        }
    }
}
