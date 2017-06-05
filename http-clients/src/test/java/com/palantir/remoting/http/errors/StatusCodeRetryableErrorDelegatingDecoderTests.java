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

package com.palantir.remoting.http.errors;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import feign.Response;
import feign.RetryableException;
import feign.codec.ErrorDecoder;
import java.util.Collection;
import org.junit.Test;

public final class StatusCodeRetryableErrorDelegatingDecoderTests {

    private ErrorDecoder mockDelegate = mock(ErrorDecoder.class);
    private Response.Body mockBody = mock(Response.Body.class);

    @Test
    public void testReturnsDelegateExceptionIfErrorCodeDoesNotMatch() {
        Exception testException = new RuntimeException();
        Response testResponse = Response.create(
                javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode(), "testReason",
                ImmutableMap.<String, Collection<String>>of(),
                mockBody);
        when(mockDelegate.decode(org.mockito.Matchers.<String>any(), org.mockito.Matchers.<Response>any())).thenReturn(
                testException);

        StatusCodeRetryableErrorDelegatingDecoder decoder = new StatusCodeRetryableErrorDelegatingDecoder(
                ImmutableSet.<Integer>of(), mockDelegate);
        Exception decodedException = decoder.decode("testKey", testResponse);

        assertThat(decodedException, sameInstance(testException));
    }

    @Test
    public void testReturnsWrappedDelegateExceptionIfErrorCodeMatches() {
        Exception testException = new RuntimeException();
        Response testResponse = Response.create(
                javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode(), "testReason",
                ImmutableMap.<String, Collection<String>>of(),
                mockBody);
        when(mockDelegate.decode(org.mockito.Matchers.<String>any(), org.mockito.Matchers.<Response>any())).thenReturn(
                testException);

        StatusCodeRetryableErrorDelegatingDecoder decoder = new StatusCodeRetryableErrorDelegatingDecoder(
                ImmutableSet.of(javax.ws.rs.core.Response.Status.NOT_FOUND.getStatusCode()), mockDelegate);
        Exception decodedException = decoder.decode("testKey", testResponse);

        assertThat(decodedException, instanceOf(RetryableException.class));
        assertThat(decodedException.getMessage(), is("Error 404"));
        assertThat(decodedException.getCause(), sameInstance((Throwable) testException));
    }

}
