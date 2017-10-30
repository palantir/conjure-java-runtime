/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting3.okhttp;

import java.io.IOException;

/**
 * We want RuntimeExceptions eventually, but we have to wrap them up in an IOException to convince the {@link
 * okhttp3.RealCall} method to propagate them nicely.
 * <p>
 * They're unwrapped right at the top of the stack by our RemoteIOExceptionClient.
 */
public final class RemoteIoException extends IOException {
    public RemoteIoException(RuntimeException runtimeException) {
        super(runtimeException);
    }

    public RuntimeException getRuntimeExceptionCause() {
        return (RuntimeException) getCause();
    }
}
