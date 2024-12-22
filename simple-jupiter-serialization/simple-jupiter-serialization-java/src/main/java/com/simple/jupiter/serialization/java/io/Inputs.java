package com.simple.jupiter.serialization.java.io;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import com.simple.jupiter.serialization.io.InputBuf;

public final class Inputs {

    public static ObjectInputStream getInput(InputBuf inputBuf) throws IOException {
        return new ObjectInputStream(inputBuf.inputStream());
    }

    public static ObjectInputStream getInput(byte[] bytes, int offset, int length) throws IOException {
        return new ObjectInputStream(new ByteArrayInputStream(bytes, offset, length));
    }

    private Inputs() {}
}
