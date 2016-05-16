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

package com.palantir.tracing;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.base.Optional;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.UUID;
import org.immutables.value.Value;

/**
 * A container object used to hold information about call tracing.
 */
@JsonDeserialize(as = ImmutableTraceState.class)
@JsonSerialize(as = ImmutableTraceState.class)
@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
public abstract class TraceState {

    private static final Random RANDOM = new Random();

    /**
     * Return a description of the operation for this event.
     */
    public abstract String getOperation();

    /**
     * Return a globally unique identifier representing this call trace.
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Value.Default
    public String getTraceId() {
        return randomId();
    }

    /**
     * Return the identifier of the parent span for the current span, if one exists.
     */
    public abstract Optional<String> getParentSpanId();

    /**
     * Return a globally unique identifier representing a single span within the call trace.
     */
    @SuppressWarnings("checkstyle:designforextension")
    @Value.Default
    public String getSpanId() {
        return randomId();
    }

    public static final Builder builder() {
        return new Builder();
    }

    public static String randomId() {
        // non-secure random generated UUID because speed is important here and security is not
        byte[] randomBytes = new byte[16];
        RANDOM.nextBytes(randomBytes);

        randomBytes[6]  &= 0x0f;  /* clear version        */
        randomBytes[6]  |= 0x40;  /* set to version 4     */
        randomBytes[8]  &= 0x3f;  /* clear variant        */
        randomBytes[8]  |= 0x80;  /* set to IETF variant  */

        ByteBuffer buffer = ByteBuffer.wrap(randomBytes);
        long msb = buffer.getLong(0);
        long lsb = buffer.getLong(8);
        return new UUID(msb, lsb).toString();
    }

    public static class Builder extends ImmutableTraceState.Builder {}

}
