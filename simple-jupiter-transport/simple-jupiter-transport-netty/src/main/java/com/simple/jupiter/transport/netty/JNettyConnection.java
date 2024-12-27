package com.simple.jupiter.transport.netty;

import com.simple.jupiter.transport.JConnection;
import com.simple.jupiter.transport.UnresolvedAddress;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

public abstract class JNettyConnection extends JConnection {

    private final ChannelFuture future;

    public JNettyConnection(UnresolvedAddress address, ChannelFuture future) {
        super(address);
        this.future = future;
    }

    public ChannelFuture getFuture() {
        return future;
    }

    @Override
    public void operationComplete(final OperationListener operationListener) {
        future.addListener((ChannelFutureListener) future -> operationListener.complete(future.isSuccess()));
    }
}
