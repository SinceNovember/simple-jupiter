package com.simple.jupiter.serialization.java;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import com.simple.jupiter.serialization.Serializer;
import com.simple.jupiter.serialization.SerializerType;
import com.simple.jupiter.serialization.io.InputBuf;
import com.simple.jupiter.serialization.io.OutputBuf;
import com.simple.jupiter.serialization.io.OutputStreams;
import com.simple.jupiter.serialization.java.io.Inputs;
import com.simple.jupiter.serialization.java.io.Outputs;
import com.simple.jupiter.util.ThrowUtil;

public class JavaSerializer extends Serializer {

    @Override
    public byte code() {
        return SerializerType.JAVA.value();
    }

    @Override
    public <T> OutputBuf writeObject(OutputBuf outputBuf, T obj) {
        try (ObjectOutputStream output = Outputs.getOutput(outputBuf)) {
            output.writeObject(obj);
            output.flush();
            return outputBuf;
        } catch (IOException e) {
            ThrowUtil.throwException(e);
        }
        return null;
    }

    @Override
    public <T> byte[] writeObject(T obj) {
        ByteArrayOutputStream buf = OutputStreams.getByteArrayOutputStream();
        try (ObjectOutputStream output = Outputs.getOutput(buf)) {
            output.writeObject(obj);
            output.flush();
            return buf.toByteArray();
        } catch (IOException e) {
            ThrowUtil.throwException(e);
        } finally {
            OutputStreams.resetBuf(buf);
        }
        return null; // never get here
    }

    @Override
    public <T> T readObject(InputBuf inputBuf, Class<T> clazz) {
        try (ObjectInputStream input = Inputs.getInput(inputBuf)) {
            Object obj = input.readObject();
            return clazz.cast(obj);
        } catch (Exception e) {
            ThrowUtil.throwException(e);
        } finally {
            inputBuf.release();
        }
        return null; // never get here
    }

    @Override
    public <T> T readObject(byte[] bytes, int offset, int length, Class<T> clazz) {
        try (ObjectInputStream input = Inputs.getInput(bytes, offset, length)) {
            Object obj = input.readObject();
            return clazz.cast(obj);
        } catch (Exception e) {
            ThrowUtil.throwException(e);
        }
        return null; // never get here
    }

    @Override
    public String toString() {
        return "java:(code=" + code() + ")";
    }
}
