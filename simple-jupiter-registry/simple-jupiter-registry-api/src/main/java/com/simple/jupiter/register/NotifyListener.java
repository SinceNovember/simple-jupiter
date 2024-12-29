package com.simple.jupiter.register;

public interface NotifyListener {

    void notify(RegisterMeta registerMeta, NotifyEvent event);

    enum NotifyEvent {
        CHILD_ADDED,
        CHILD_REMOVED
    }

}
