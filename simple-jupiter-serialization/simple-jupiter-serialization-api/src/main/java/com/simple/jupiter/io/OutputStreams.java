package com.simple.jupiter.io;

import java.io.ByteArrayOutputStream;
import com.simple.jupiter.util.internal.ReferenceFieldUpdater;
import com.simple.jupiter.util.internal.Updaters;

public final class OutputStreams {

    private static final ReferenceFieldUpdater<ByteArrayOutputStream, byte[]> bufUpdater =
        Updaters.newReferenceFieldUpdater(ByteArrayOutputStream.class, "buf");
}
