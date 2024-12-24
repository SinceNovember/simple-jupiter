package com.simple.jupiter.transport;

public interface Transporter {

    Protocol protocol();

    /**
     * 传输层协议
     */
    enum Protocol {
        TCP,
        DOMAIN
    }

}
