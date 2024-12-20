package com.simple.jupiter.util.internal;

import com.lmax.disruptor.dsl.Disruptor;

public class Updaters {


    public static <U> IntegerFieldUpdater<U> newIntegerFieldUpdater(Class<? super U> tClass, String fieldName) {
        try {
            if (UnsafeUtil.hasUnsafe()) {
                return new UnsafeIntegerFieldUpdater<>(UnsafeUtil.getUnsafeAccessor().getUnsafe(), tClass, fieldName);
            } else {
                return new ReflectionIntegerFieldUpdater<>(tClass, fieldName);
            }
        }  catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static <U> LongFieldUpdater<U> newLongFieldUpdater(Class<? super U> tClass, String fieldName) {
        try {
            if (UnsafeUtil.hasUnsafe()) {
                return new UnsafeLongFieldUpdater<>(UnsafeUtil.getUnsafeAccessor().getUnsafe(), tClass, fieldName);
            } else {
                return new ReflectionLongFieldUpdater<>(tClass, fieldName);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static <U, W> ReferenceFieldUpdater<U, W> newReferenceFieldUpdater(Class<? super U> tClass, String fieldName) {
        try {
            if (UnsafeUtil.hasUnsafe()) {
                return new UnsafeReferenceFieldUpdater<>(UnsafeUtil.getUnsafeAccessor().getUnsafe(), tClass, fieldName);
            } else {
                return new ReflectionReferenceFieldUpdater<>(tClass, fieldName);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
