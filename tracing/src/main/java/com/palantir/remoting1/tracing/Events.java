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

import java.util.concurrent.TimeUnit;

public final class Events {

    private Events() {}

    /** The denotes the event of a server receiving a message. */
    public static Event serverReceive(long timestamp, TimeUnit timeUnit) {
        return event("sr", timestamp, timeUnit);
    }

    /** The denotes the event of a server sending the response to a message. */
    public static Event serverSend(long timestamp, TimeUnit timeUnit) {
        return event("ss", timestamp, timeUnit);
    }

    /** The denotes the event of a client sending a message. */
    public static Event clientStart(long timestamp, TimeUnit timeUnit) {
        return event("cs", timestamp, timeUnit);
    }

    /** The denotes the event of a client receiving the response to a message. */
    public static Event clientReceive(long timestamp, TimeUnit timeUnit) {
        return event("cr", timestamp, timeUnit);
    }

    private static Event event(String type, long timestamp, TimeUnit timeUnit) {
        return ImmutableEvent.builder()
                .type(type)
                .epochMicroSeconds(timeUnit.toMicros(timestamp))
                .build();
    }
}
