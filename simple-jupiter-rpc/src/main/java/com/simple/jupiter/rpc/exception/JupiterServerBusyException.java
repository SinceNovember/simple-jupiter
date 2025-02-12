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
package com.simple.jupiter.rpc.exception;

/**
 * Usually it means that the server can not handle the new request.
 *
 * For efficiency this exception will not have a stack trace.
 *
 * jupiter
 * org.jupiter.rpc.exception
 *
 * @author jiachun.fjc
 */
public class JupiterServerBusyException extends JupiterRemoteException {

    private static final long serialVersionUID = 4812626729436624336L;

    public JupiterServerBusyException() {}

    public JupiterServerBusyException(String message) {
        super(message);
    }

    public JupiterServerBusyException(String message, Throwable cause) {
        super(message, cause);
    }

    public JupiterServerBusyException(Throwable cause) {
        super(cause);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
