package com.simple.jupiter.serialization.java.io;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import com.simple.jupiter.serialization.io.OutputBuf;

public final class Outputs {

    public static ObjectOutputStream getOutput(OutputBuf outputBuf) throws IOException {
        return new ObjectOutputStream(outputBuf.outputStream());
    }

    public static ObjectOutputStream getOutput(OutputStream buf) throws IOException {
        return new ObjectOutputStream(buf);
    }

    private Outputs() {}
}
