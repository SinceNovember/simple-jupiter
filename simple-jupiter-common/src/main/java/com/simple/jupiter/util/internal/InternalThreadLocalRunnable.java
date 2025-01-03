/*
 * Copyright (c) 2015 The Jupiter Project
 *
 * Licensed under the Apache License, version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.simple.jupiter.util.internal;


import com.simple.jupiter.util.Requires;

/**
 * jupiter
 * org.jupiter.common.util.internal
 *
 * @author jiachun.fjc
 */
public class InternalThreadLocalRunnable implements Runnable {

    private final Runnable runnable;

    private InternalThreadLocalRunnable(Runnable runnable) {
        this.runnable = Requires.requireNotNull(runnable, "runnable");
    }

    @Override
    public void run() {
        try {
            runnable.run();
        } finally {
            InternalThreadLocal.removeAll();
        }
    }

    public static Runnable wrap(Runnable runnable) {
        return runnable instanceof InternalThreadLocalRunnable ? runnable : new InternalThreadLocalRunnable(runnable);
    }
}
