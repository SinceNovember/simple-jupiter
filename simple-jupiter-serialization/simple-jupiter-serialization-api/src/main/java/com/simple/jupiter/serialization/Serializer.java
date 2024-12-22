package com.simple.jupiter.serialization;

import com.simple.jupiter.serialization.io.InputBuf;
import com.simple.jupiter.serialization.io.OutputBuf;

public abstract class Serializer {


    /**
     * The max buffer size for a {@link Serializer} to cached.
     */
    public static final int MAX_CACHED_BUF_SIZE = 256 * 1024;

    /**
     * The default buffer size for a {@link Serializer}.
     */
    public static final int DEFAULT_BUF_SIZE = 512;

    public abstract byte code();

    public abstract <T> OutputBuf writeObject(OutputBuf outputBuf, T obj);

    public abstract <T> byte[] writeObject(T obj);

    public abstract <T> T readObject(InputBuf inputBuf, Class<T> clazz);

    public abstract <T> T readObject(byte[] bytes, int offset, int length, Class<T> clazz);

    public <T> T readObject(byte[] bytes, Class<T> clazz) {
        return readObject(bytes, 0, bytes.length, clazz);
    }

}
