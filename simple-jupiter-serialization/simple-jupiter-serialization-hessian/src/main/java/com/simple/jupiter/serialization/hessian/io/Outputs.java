package com.simple.jupiter.serialization.hessian.io;

import java.io.OutputStream;
import com.caucho.hessian.io.Hessian2Output;
import com.simple.jupiter.serialization.io.OutputBuf;

public class Outputs {

    public static Hessian2Output getOutput(OutputBuf outputBuf) {
        return new Hessian2Output(outputBuf.outputStream());
    }

    public static Hessian2Output getOutput(OutputStream buf) {
        return new Hessian2Output(buf);
    }

    private Outputs() {}
}
