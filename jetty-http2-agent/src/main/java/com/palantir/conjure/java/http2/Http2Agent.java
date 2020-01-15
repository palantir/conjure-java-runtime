/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.http2;

import java.lang.instrument.Instrumentation;

/**
 * No-op.
 *
 * @deprecated this class no longer has any effect.
 */
@Deprecated
public final class Http2Agent {
    private Http2Agent() {}

    /**
     * No-op.
     *
     * @deprecated this method no longer has any effect and is safe to stop calling.
     */
    @Deprecated
    public static void install() {
        // no-op
    }

    /**
     * No-op.
     *
     * @deprecated this method no longer has any effect and is safe to stop calling.
     */
    @Deprecated
    public static void agentmain(String _args, Instrumentation _inst) throws Exception {
        // no-op
    }
}
