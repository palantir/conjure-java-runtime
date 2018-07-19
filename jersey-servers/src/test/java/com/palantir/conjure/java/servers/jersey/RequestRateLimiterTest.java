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

package com.palantir.conjure.java.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class RequestRateLimiterTest {

    private static final long REQUESTS_PER_SECOND = 10;
    private static final long ALLOWED_DELAY = 1_000 / REQUESTS_PER_SECOND;
    private static final long NO_DELAY = 0;

    @Mock
    private Function<ContainerRequestContext, Optional<String>> featureFunc;
    private ArgumentCaptor<Response> responseCaptor = ArgumentCaptor.forClass(Response.class);

    @Mock
    private ContainerRequestContext request1;
    @Mock
    private ContainerRequestContext request2;

    private RequestRateLimiter rateLimiter;

    @Before
    public void before() throws InterruptedException {
        MockitoAnnotations.initMocks(this);
        rateLimiter = RequestRateLimiter.create(REQUESTS_PER_SECOND, featureFunc);

        when(featureFunc.apply(request1)).thenReturn(Optional.of("1"));
        when(featureFunc.apply(request2)).thenReturn(Optional.of("2"));
    }

    @Test
    public void testRequestAborted() throws IOException {
        execute(NO_DELAY, request1, request1);

        verify(request1).abortWith(responseCaptor.capture());
        assertThat(responseCaptor.getValue().getStatus()).isEqualTo(429);
    }

    @Test
    public void testDifferentFeaturesNotLimited() {
        execute(NO_DELAY, request1, request2);

        verify(request1, never()).abortWith(any());
        verify(request2, never()).abortWith(any());
    }

    @Test
    public void testUnderLimitAllowed() {
        List<ContainerRequestContext> requests = Collections.nCopies(10, request1);
        execute(ALLOWED_DELAY, requests);

        verify(request1, never()).abortWith(any());
    }

    @Test
    public void testLimiterRemembersAndAllowsBursts() throws InterruptedException {
        execute(NO_DELAY, request1);

        Thread.sleep(1_000);

        List<ContainerRequestContext> requests = Collections.nCopies(10, request1);
        execute(NO_DELAY, requests);

        verify(request1, never()).abortWith(any());
    }

    @Test
    public void testNoLimitingWhenTooManyUniqueFeatures() throws IOException {
        rateLimiter = new RequestRateLimiter<>(1, 1, featureFunc);

        execute(NO_DELAY, request1, request2, request1, request2);

        verify(request1, never()).abortWith(any());
        verify(request2, never()).abortWith(any());
    }

    private void execute(long delay, ContainerRequestContext... requests) {
        execute(delay, Arrays.asList(requests));
    }

    private void execute(long delay, List<ContainerRequestContext> requests) {
        requests.forEach(request -> {
            try {
                rateLimiter.filter(request);
                Thread.sleep(delay);
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
