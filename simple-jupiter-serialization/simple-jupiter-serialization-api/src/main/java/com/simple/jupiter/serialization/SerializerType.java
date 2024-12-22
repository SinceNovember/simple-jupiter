package com.simple.jupiter.serialization;

/**
 * 最多可以扩展到15种不同的序列化/反序列化方式, 取值范围为: 0x01 ~ 0x0f,
 * 每当想增加一种序列化/反序列化方式都要修改这个枚举, 不过我想这个限制是值得的.
 *
 * jupiter
 * org.jupiter.serialization
 *
 * @author jiachun.fjc
 */
public enum SerializerType {
    PROTO_STUFF ((byte) 0x01),
    HESSIAN     ((byte) 0x02),
    KRYO        ((byte) 0x03),
    JAVA        ((byte) 0x04)
    // ...
    ;

    private final byte value;

    SerializerType(byte value) {
        if (0x00 < value && value < 0x0f) {
            this.value = value;
        } else {
            throw new IllegalArgumentException("Out of range(0x01 ~ 0x0f): " + value);
        }
    }

    public byte value() {
        return value;
    }

    public static SerializerType parse(String name) {
        for (SerializerType s : values()) {
            if (s.name().equalsIgnoreCase(name)) {
                return s;
            }
        }
        return null;
    }

    public static SerializerType parse(byte value) {
        for (SerializerType s : values()) {
            if (s.value() == value) {
                return s;
            }
        }
        return null;
    }

    public static SerializerType getDefault() {
        return PROTO_STUFF;
    }
}
