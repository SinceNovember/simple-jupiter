package com.simple.jupiter.tracing;

import java.util.Iterator;
import com.simple.jupiter.util.JServiceLoader;
import com.simple.jupiter.util.StackTraceUtil;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;

public interface TracerFactory {

    TracerFactory DEFAULT = new DefaultTracerFactory();

    /**
     * Get a {@link Tracer} implementation.
     */
    Tracer getTracer();

    class DefaultTracerFactory implements TracerFactory {

        private static final InternalLogger logger = InternalLoggerFactory.getInstance(DefaultTracerFactory.class);

        private static Tracer tracer = loadTracer();

        private static Tracer loadTracer() {
            try {
                Iterator<Tracer> implementations = JServiceLoader.load(Tracer.class).iterator();
                if (implementations.hasNext()) {
                    Tracer first = implementations.next();
                    if (!implementations.hasNext()) {
                        return first;
                    }

                    logger.warn("More than one tracer is found, NoopTracer will be used as default.");

                    return NoopTracerFactory.create();
                }
            } catch (Throwable t) {
                logger.error("Load tracer failed: {}.", StackTraceUtil.stackTrace(t));
            }
            return NoopTracerFactory.create();
        }

        @Override
        public Tracer getTracer() {
            return tracer;
        }
    }
}
