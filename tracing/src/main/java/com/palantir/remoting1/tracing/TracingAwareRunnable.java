/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.tracing;

import java.util.Deque;

public final class TracingAwareRunnable implements Runnable {

    private final Runnable delegate;
    private final Deque<TraceState> state;

    public TracingAwareRunnable(Runnable delegate) {
        this.delegate = delegate;
        this.state = Traces.getCopyOfState();
    }

    @Override
    public void run() {
        Deque<TraceState> previousState = Traces.getCopyOfState();
        Traces.forceState(state);
        try {
            delegate.run();
        } finally {
            Traces.forceState(previousState);
        }
    }
}
