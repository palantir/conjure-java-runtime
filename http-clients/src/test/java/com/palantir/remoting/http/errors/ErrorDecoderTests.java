/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http.errors;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableMap;
import feign.Response;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import javax.annotation.CheckForNull;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Tests for general logic in ErrorDecoder. More tests for the specific converters are in the error-handling project.
 */
@RunWith(Parameterized.class)
public final class ErrorDecoderTests {

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                {ErrorDecoderImpl.SERIALIZABLE_ERROR }, {ErrorDecoderImpl.GO_ERROR }
        });
    }

    private final ErrorDecoderImpl decoder;

    public ErrorDecoderTests(ErrorDecoderImpl decoder) {
        this.decoder = decoder;
    }

    @Test
    public void testSanity() {
        Response response = getResponse(MediaType.TEXT_PLAIN, "errorbody");
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(), is("Error 400. Reason: reason. Body:\nerrorbody"));
    }

    @Test
    public void testNoContentType() {
        Response response = Response.create(400, "reason", ImmutableMap.<String, Collection<String>>of(), "errorbody",
                feign.Util.UTF_8);
        Exception decode = decoder.decode("ignored", response);
        assertThat(decode, is(instanceOf(RuntimeException.class)));
        assertThat(decode.getMessage(),
                is("Error 400. Reason: reason. Body content type: []. Body as String: errorbody"));
    }

    private static Response getResponse(String contentType, @CheckForNull String body) {
        return getResponse(contentType, body, 400);
    }

    private static Response getResponse(String contentType, @CheckForNull String body, int status) {
        return Response.create(status, "reason", ImmutableMap.<String, Collection<String>>of(HttpHeaders.CONTENT_TYPE,
                Collections.singletonList(contentType)), body, feign.Util.UTF_8);
    }
}
