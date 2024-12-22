package com.simple.jupiter.serialization.io;

import java.io.ByteArrayOutputStream;
import static com.simple.jupiter.serialization.Serializer.DEFAULT_BUF_SIZE;
import static com.simple.jupiter.serialization.Serializer.MAX_CACHED_BUF_SIZE;
import com.simple.jupiter.util.internal.InternalThreadLocal;
import com.simple.jupiter.util.internal.ReferenceFieldUpdater;
import com.simple.jupiter.util.internal.Updaters;

public final class OutputStreams {

    private static final ReferenceFieldUpdater<ByteArrayOutputStream, byte[]> bufUpdater =
        Updaters.newReferenceFieldUpdater(ByteArrayOutputStream.class, "buf");

    // 复用 ByteArrayOutputStream 中的 byte[]
    private static final InternalThreadLocal<ByteArrayOutputStream> bufThreadLocal =
        new InternalThreadLocal<ByteArrayOutputStream>() {

            @Override
            protected ByteArrayOutputStream initialValue() {
                return new ByteArrayOutputStream(DEFAULT_BUF_SIZE);
            }
        };

    public static ByteArrayOutputStream getByteArrayOutputStream() {
        return bufThreadLocal.get();
    }

    public static void resetBuf(ByteArrayOutputStream buf) {
        buf.reset(); // for reuse

        // 防止hold过大的内存块一直不释放
        if (bufUpdater.get(buf).length > MAX_CACHED_BUF_SIZE) {
            bufUpdater.set(buf, new byte[DEFAULT_BUF_SIZE]);
        }
    }

}