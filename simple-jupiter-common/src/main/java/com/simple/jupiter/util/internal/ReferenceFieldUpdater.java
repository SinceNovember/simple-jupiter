package com.simple.jupiter.util.internal;

public interface ReferenceFieldUpdater<U, W> {

    void set(U obj, W newValue);

    W get(U obj);
}
