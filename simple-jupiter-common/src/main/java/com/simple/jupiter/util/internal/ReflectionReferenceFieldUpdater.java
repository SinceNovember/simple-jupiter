package com.simple.jupiter.util.internal;

import java.lang.reflect.Field;

/**
 * 通过java反射来获取/设置指定字段值
 * @param <U>
 * @param <W>
 */
public class ReflectionReferenceFieldUpdater<U, W> implements ReferenceFieldUpdater<U, W>{

    private final Field field;

    public ReflectionReferenceFieldUpdater(Class<? super U> tClass, String fieldName) throws NoSuchFieldException {
        this.field = tClass.getDeclaredField(fieldName);
        this.field.setAccessible(true);
    }

    @Override
    public void set(U obj, W newValue) {
        try {
            field.set(obj, newValue);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public W get(U obj) {
        try {
            return (W) field.get(obj);
        } catch (IllegalAccessException e) {
            throw new RuntimeException();
        }
    }
}
