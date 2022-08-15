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

package com.palantir.conjure.java.server.jersey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.ws.rs.BadRequestException;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

public final class WebPreconditionsTest {
    @Nullable
    private static Object nullReference = null; // circumvent findbugs

    @Test
    public void testCheckNotNullWithMessage() {
        String message = "here's a message";

        assertThatThrownBy(() -> {
                    WebPreconditions.checkNotNull(nullReference, message);
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
    }

    @Test
    public void testCheckNotNullWithNullMessage() {
        String message = null;

        assertThatThrownBy(() -> {
                    WebPreconditions.checkNotNull(nullReference, message);
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage("null");
    }

    @Test
    public void testCheckNotNullWithMessageAndValues() {
        String message = "message with %s %s";
        String replacedMessage = "message with two percents";

        assertThatThrownBy(() -> {
                    WebPreconditions.checkNotNull(nullReference, message, "two", "percents");
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage(replacedMessage);
    }

    @Test
    public void testCheckNotNullNoExceptions() {
        Object reference = new Object();

        assertThat(WebPreconditions.checkNotNull(reference, "message")).isSameAs(reference);
        assertThat(WebPreconditions.checkNotNull(reference, "message", "args")).isSameAs(reference);
    }

    @Test
    public void testCheckNotEmptyWithMessage() {
        String message = "here's a message";

        assertThatThrownBy(() -> {
                    WebPreconditions.checkNotEmpty("", message);
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
    }

    @Test
    public void testCheckNotEmptyWithNullMessage() {
        String message = null;

        assertThatThrownBy(() -> {
                    WebPreconditions.checkNotEmpty("", message);
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage("null");
    }

    @Test
    public void testCheckNotEmptyWithMessageAndValues() {
        String message = "message with %s %s";
        String replacedMessage = "message with two percents";

        assertThatThrownBy(() -> {
                    WebPreconditions.checkNotEmpty("", message, "two", "percents");
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage(replacedMessage);
    }

    @Test
    public void testCheckNotEmptyNoExceptions() {
        String string = "here's a string";

        assertThat(WebPreconditions.checkNotEmpty(string, "message")).isSameAs(string);
        assertThat(WebPreconditions.checkNotEmpty(string, "message", "args")).isSameAs(string);
    }

    @Test
    public void testCheckArgumentWithMessage() {
        String message = "here's a message";

        assertThatThrownBy(() -> {
                    WebPreconditions.checkArgument(false, message);
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage(message);
    }

    @Test
    public void testCheckArgumentWithNullMessage() {
        String message = null;

        assertThatThrownBy(() -> {
                    WebPreconditions.checkArgument(false, message);
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage("null");
    }

    @Test
    public void testCheckArgumentWithMessageAndValues() {
        String message = "message with %s %s";
        String replacedMessage = "message with two percents";

        assertThatThrownBy(() -> {
                    WebPreconditions.checkArgument(false, message, "two", "percents");
                })
                .isInstanceOf(BadRequestException.class)
                .hasMessage(replacedMessage);
    }

    @Test
    public void testCheckArgumentNoExceptions() {
        WebPreconditions.checkArgument(true, "message");
        WebPreconditions.checkArgument(true, "message", "args");
    }
}
