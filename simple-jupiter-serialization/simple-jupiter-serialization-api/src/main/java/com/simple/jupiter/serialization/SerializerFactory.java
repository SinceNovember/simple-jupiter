package com.simple.jupiter.serialization;

import com.simple.jupiter.util.JServiceLoader;
import com.simple.jupiter.util.collection.ByteObjectHashMap;
import com.simple.jupiter.util.collection.ByteObjectMap;
import com.simple.jupiter.util.internal.logging.InternalLogger;
import com.simple.jupiter.util.internal.logging.InternalLoggerFactory;

public final class SerializerFactory {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(SerializerFactory.class);

    private static final ByteObjectMap<Serializer> serializers = new ByteObjectHashMap<>();

    static {
        Iterable<Serializer> all = JServiceLoader.load(Serializer.class);
        for (Serializer s : all) {
            serializers.put(s.code(), s);
        }
        logger.info("Supported serializers: {}.", serializers);
    }

    public static Serializer getSerializer(byte code) {
        Serializer serializer = serializers.get(code);

        if (serializer == null) {
            SerializerType type = SerializerType.parse(code);
            if (type != null) {
                throw new IllegalArgumentException("Serializer implementation [" + type.name() + "] not found");
            } else {
                throw new IllegalArgumentException("Unsupported serializer type with code: " + code);
            }
        }

        return serializer;
    }

    private SerializerFactory() {}


}
