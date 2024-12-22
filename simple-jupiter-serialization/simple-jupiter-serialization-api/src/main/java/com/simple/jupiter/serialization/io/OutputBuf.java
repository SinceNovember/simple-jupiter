package com.simple.jupiter.serialization.io;

import java.io.OutputStream;
import java.nio.ByteBuffer;

public interface OutputBuf {

    /**
     * Exposes this backing data as an {@link OutputStream}.
     */
    OutputStream outputStream();

    /**
     * Exposes this backing data as a NIO {@link ByteBuffer}.
     */
    ByteBuffer nioByteBuffer(int minWritableBytes);

    /**
     * Returns the number of readable bytes.
     */
    int size();

    /**
     * Returns {@code true} if and only if this buf has a reference to the low-level memory address that points
     * to the backing data.
     */
    boolean hasMemoryAddress();

    /**
     * Returns the backing object.
     */
    Object backingObject();
}
