package com.simple.jupiter.transport;

public abstract class JConnection {

    private final UnresolvedAddress address;

    public JConnection(UnresolvedAddress address) {
        this.address = address;
    }

    public UnresolvedAddress getAddress() {
        return address;
    }

    public void operationComplete(@SuppressWarnings("unused") OperationListener operationListener) {
        // the default implementation does nothing
    }

    public abstract void setReconnect(boolean reconnect);

    public interface OperationListener {

        void complete(boolean isSuccess);
    }
}
