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

package com.palantir.remoting2.jaxrs.feignimpl;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import feign.Response;
import feign.RetryableException;
import java.util.Collection;
import java.util.Collections;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.junit.Test;

public final class FeignSerializableErrorErrorDecoderTests {
    // most tests are in SerializableErrorErrorDecoderTests
    private static final FeignSerializableErrorErrorDecoder decoder = FeignSerializableErrorErrorDecoder.INSTANCE;

    @Test
    public void testSanity() {
        String expectedMessage = "Error 400. Reason: reason. Body:\nerrorbody";
        checkExceptionAndMessage(getResponse(HttpHeaders.CONTENT_TYPE), expectedMessage);
    }

    @Test
    public void testSanityWithArbitraryHeaderCapitalization() {
        String expectedMessage = "Error 400. Reason: reason. Body:\nerrorbody";
        checkExceptionAndMessage(getResponse("ConteNT-TYPE"), expectedMessage);
    }

    @Test
    public void testNoContentType() {
        Response response = Response.create(400, "reason", ImmutableMap.<String, Collection<String>>of(), "errorbody",
                feign.Util.UTF_8);
        String expectedMessage = "Error 400. Reason: reason. Body:\nerrorbody";
        checkExceptionAndMessage(response, expectedMessage);
    }

    @Test
    public void testRetryAfter() {
        Response response = Response.create(
                503,
                "reason",
                ImmutableMap.of(HttpHeaders.RETRY_AFTER, ImmutableSet.of("0")),
                "errorbody",
                feign.Util.UTF_8);
        Exception exception = decoder.decode("ignored", response);
        assertThat(exception, is(instanceOf(RetryableException.class)));
    }

    @Test
    public void testInvalidRetryAfter() {
        Response response = Response.create(
                503,
                "reason",
                ImmutableMap.of(HttpHeaders.RETRY_AFTER, ImmutableSet.of("abc")),
                "errorbody",
                feign.Util.UTF_8);
        Exception exception = decoder.decode("ignored", response);
        String expectedMessage = "Error 503. Reason: reason. Body:\nerrorbody";
        checkExceptionAndMessage(response, expectedMessage);
    }

    private void checkExceptionAndMessage(Response response, String expectedMessage) {
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is(expectedMessage));
    }

    private static Response getResponse(String headerName) {
        return Response.create(400, "reason", ImmutableMap.<String, Collection<String>>of(headerName,
                Collections.singletonList(MediaType.TEXT_PLAIN)), "errorbody", feign.Util.UTF_8);
    }
}
