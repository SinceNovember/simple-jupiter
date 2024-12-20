package com.simple.jupiter.util.internal;

import java.lang.reflect.Field;
import sun.misc.Unsafe;

/**
 * cas的方式设置/获取对应对象的Field值
 * @param <U>
 * @param <W>
 */
public class UnsafeReferenceFieldUpdater <U, W> implements ReferenceFieldUpdater<U, W> {

    private final long offset;
    private final Unsafe unsafe;

    UnsafeReferenceFieldUpdater(Unsafe unsafe, Class<? super U> tClass, String fieldName) throws NoSuchFieldException {
        final Field field = tClass.getDeclaredField(fieldName);
        if (unsafe == null) {
            throw new NullPointerException("unsafe");
        }
        this.unsafe = unsafe;
        offset = unsafe.objectFieldOffset(field);
    }

    @Override
    public void set(U obj, W newValue) {
        unsafe.putObject(obj, offset, newValue);
    }

    @Override
    public W get(U obj) {
        return (W) unsafe.getObject(obj, offset);
    }

}
