/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.remoting3.tracing;

import com.palantir.tracing.ExposedTrace;

public final class TestConvert {

    private TestConvert() {}

    /** Warning - this is NOT a lossless copy, it loses the stack of OpenSpans in the original trace */
    public static Trace toRemotingTraceIncompleteCopy(com.palantir.tracing.Trace newTrace) {
        if (newTrace == null) {
            return null;
        }

        return new Trace(ExposedTrace.isObservable(newTrace), ExposedTrace.getTraceId(newTrace));
    }
}
