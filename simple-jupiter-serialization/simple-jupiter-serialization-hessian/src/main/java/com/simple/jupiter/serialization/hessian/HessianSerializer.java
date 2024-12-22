package com.simple.jupiter.serialization.hessian;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import com.caucho.hessian.io.Hessian2Input;
import com.caucho.hessian.io.Hessian2Output;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerType;
import com.simple.jupiter.serialization.io.InputBuf;
import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.serialization.io.OutputStreams;
import com.simple.jupiter.serialization.hessian.io.Inputs;
import com.simple.jupiter.serialization.hessian.io.Outputs;
import com.simple.jupiter.util.ThrowUtil;

public class HessianSerializer extends Serializer {

    @Override
    public byte code() {
        return SerializerType.HESSIAN.value();
    }

    @Override
    public <T> OutputBuf writeObject(OutputBuf outputBuf, T obj) {
        Hessian2Output output = Outputs.getOutput(outputBuf);
        try {
            output.writeObject(obj);
            output.flush();
            return outputBuf;
        } catch (IOException e) {
            ThrowUtil.throwException(e);
        } finally {
            try {
                output.close();
            } catch (IOException ignored) {}
        }
        return null; // never get here
    }

    @Override
    public <T> byte[] writeObject(T obj) {
        ByteArrayOutputStream buf = OutputStreams.getByteArrayOutputStream();
        Hessian2Output output = Outputs.getOutput(buf);
        try {
            output.writeObject(obj);
            output.flush();
            return buf.toByteArray();
        } catch (IOException e) {
            ThrowUtil.throwException(e);
        } finally {
            try {
                output.close();
            } catch (IOException ignored) {}

            OutputStreams.resetBuf(buf);
        }
        return null; // never get here
    }

    @Override
    public <T> T readObject(InputBuf inputBuf, Class<T> clazz) {
        Hessian2Input input = Inputs.getInput(inputBuf);
        try {
            Object obj = input.readObject(clazz);
            return clazz.cast(obj);
        } catch (IOException e) {
            ThrowUtil.throwException(e);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {}

            inputBuf.release();
        }
        return null; // never get here
    }

    @Override
    public <T> T readObject(byte[] bytes, int offset, int length, Class<T> clazz) {
        Hessian2Input input = Inputs.getInput(bytes, offset, length);
        try {
            Object obj = input.readObject(clazz);
            return clazz.cast(obj);
        } catch (IOException e) {
            ThrowUtil.throwException(e);
        } finally {
            try {
                input.close();
            } catch (IOException ignored) {}
        }
        return null; // never get here
    }

    @Override
    public String toString() {
        return "hessian:(code=" + code() + ")";
    }
}
