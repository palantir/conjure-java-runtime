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

package com.palantir.remoting.http.server;

import static org.junit.Assert.assertSame;

import javax.annotation.Nullable;
import javax.ws.rs.BadRequestException;
import org.hamcrest.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class WebPreconditionsTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Nullable
    private static Object nullReference = null; // circumvent findbugs

    @Test
    public void testCheckNotNullWithMessage() {
        String message = "here's a message";

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is(message));

        WebPreconditions.checkNotNull(nullReference, message);
    }

    @Test
    public void testCheckNotNullWithNullMessage() {
        String message = null;

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is("null"));

        WebPreconditions.checkNotNull(nullReference, message);
    }

    @Test
    public void testCheckNotNullWithMessageAndValues() {
        String message = "message with %s %s";
        String replacedMessage = "message with two percents";

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is(replacedMessage));

        WebPreconditions.checkNotNull(nullReference, message, "two", "percents");
    }

    @Test
    public void testCheckNotNullNoExceptions() {
        Object reference = new Object();

        assertSame(reference, WebPreconditions.checkNotNull(reference, "message"));
        assertSame(reference, WebPreconditions.checkNotNull(reference, "message", "args"));
    }

    @Test
    public void testCheckNotEmptyWithMessage() {
        String message = "here's a message";

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is(message));

        WebPreconditions.checkNotEmpty("", message);
    }

    @Test
    public void testCheckNotEmptyWithNullMessage() {
        String message = null;

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is("null"));

        WebPreconditions.checkNotEmpty("", message);
    }

    @Test
    public void testCheckNotEmptyWithMessageAndValues() {
        String message = "message with %s %s";
        String replacedMessage = "message with two percents";

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is(replacedMessage));

        WebPreconditions.checkNotEmpty("", message, "two", "percents");
    }

    @Test
    public void testCheckNotEmptyNoExceptions() {
        String string = "here's a string";

        assertSame(string, WebPreconditions.checkNotEmpty(string, "message"));
        assertSame(string, WebPreconditions.checkNotEmpty(string, "message", "args"));
    }

    @Test
    public void testCheckArgumentWithMessage() {
        String message = "here's a message";

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is(message));

        WebPreconditions.checkArgument(false, message);
    }

    @Test
    public void testCheckArgumentWithNullMessage() {
        String message = null;

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is("null"));

        WebPreconditions.checkArgument(false, message);
    }

    @Test
    public void testCheckArgumentWithMessageAndValues() {
        String message = "message with %s %s";
        String replacedMessage = "message with two percents";

        expectedException.expect(BadRequestException.class);
        expectedException.expectMessage(Matchers.is(replacedMessage));

        WebPreconditions.checkArgument(false, message, "two", "percents");
    }

    @Test
    public void testCheckArgumentNoExceptions() {
        WebPreconditions.checkArgument(true, "message");
        WebPreconditions.checkArgument(true, "message", "args");
    }
}
