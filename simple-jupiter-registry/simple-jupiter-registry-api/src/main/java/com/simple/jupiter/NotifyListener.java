package com.simple.jupiter;

public interface NotifyListener {

    void notify(RegisterMeta registerMeta, NotifyEvent event);

    enum NotifyEvent {
        CHILD_ADDED,
        CHILD_REMOVED
    }

}
