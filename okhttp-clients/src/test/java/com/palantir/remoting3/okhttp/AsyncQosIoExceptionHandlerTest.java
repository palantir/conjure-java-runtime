/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class AsyncQosIoExceptionHandlerTest extends TestBase {

    private static final String HTTP_URL_STRING = "http://foo";
    private static final HttpUrl HTTP_URL = HttpUrl.parse(HTTP_URL_STRING);
    private static final Request REQUEST = new Request.Builder().url(HTTP_URL_STRING).build();
    private static final Response RESPONSE = responseWithCode(REQUEST, 101 /* unused */);
    private static final Response CLONED_RESPONSE = responseWithCode(REQUEST, 102 /* unused */);
    private static final Optional<Duration> BACKOFF_10SECS = Optional.of(Duration.ofSeconds(10));
    private static final Optional<Duration> NO_BACKOFF = Optional.empty();

    @Mock
    private BackoffStrategy backoff;
    @Mock
    private Call delegateCall;
    @Mock
    private Call clonedDelegateCall;

    private DeterministicScheduler scheduler;
    private AsyncQosIoExceptionHandler handler;
    private QosIoExceptionAwareCall call;

    @Mock
    private MultiServerRequestCreator requestCreator;
    @Mock
    private Call.Factory callFactory;

    @Before
    public void before() throws Exception {
        scheduler = new DeterministicScheduler();
        handler = new AsyncQosIoExceptionHandler(scheduler,
                MoreExecutors.newDirectExecutorService(),
                backoff,
                requestCreator,
                callFactory);
        call = new QosIoExceptionAwareCall(delegateCall, handler);
        when(call.clone()).thenReturn(clonedDelegateCall);
        when(callFactory.newCall(any())).thenReturn(clonedDelegateCall);
        when(delegateCall.execute()).thenReturn(RESPONSE);
        when(clonedDelegateCall.execute()).thenReturn(CLONED_RESPONSE);

        when(delegateCall.request()).thenReturn(REQUEST);
        when(clonedDelegateCall.request()).thenReturn(REQUEST);
        when(requestCreator.getNextRequest(any())).thenReturn(REQUEST);

        doAnswer(args -> {
            Callback callback = args.getArgumentAt(0, Callback.class);
            callback.onResponse(delegateCall, CLONED_RESPONSE);
            return null;
        }).when(clonedDelegateCall).enqueue(any());
    }

    @Test
    public void testThrottle_withRetryAfter_reschedules() throws Exception {
        QosIoException exception = new QosIoException(QosException.throttle(BACKOFF_10SECS.get()), RESPONSE);
        ListenableFuture<Response> response = handler.handle(call, exception);

        scheduler.tick(5, TimeUnit.SECONDS);
        verifyNoMoreInteractions(delegateCall);
        assertThat(response.isDone()).isFalse();
        assertThat(response.isCancelled()).isFalse();

        scheduler.tick(10, TimeUnit.SECONDS);
        verify(delegateCall).clone();
        assertThat(response.isDone()).isTrue();
        assertThat(response.get(1, TimeUnit.SECONDS)).isEqualTo(CLONED_RESPONSE);
    }

    @Test
    public void testThrottle_withoutRetryAfter_reschedulesByBackoffDuration() throws Exception {
        when(backoff.nextBackoff()).thenReturn(BACKOFF_10SECS);
        QosIoException exception = new QosIoException(QosException.throttle(BACKOFF_10SECS.get()), RESPONSE);
        ListenableFuture<Response> response = handler.handle(call, exception);

        scheduler.tick(5, TimeUnit.SECONDS);
        verifyNoMoreInteractions(delegateCall);
        assertThat(response.isDone()).isFalse();
        assertThat(response.isCancelled()).isFalse();

        scheduler.tick(10, TimeUnit.SECONDS);
        verify(delegateCall).clone();
        assertThat(response.isDone()).isTrue();
        assertThat(response.get(1, TimeUnit.SECONDS)).isEqualTo(CLONED_RESPONSE);
    }

    @Test
    public void testThrottle_withoutRetryAfter_failsIfBackoffIsEmpty() throws Exception {
        when(backoff.nextBackoff()).thenReturn(NO_BACKOFF);
        QosIoException exception = new QosIoException(QosException.throttle(), RESPONSE);
        assertThatThrownBy(() -> handler.handle(call, exception).get(1, TimeUnit.SECONDS))
                .hasCause(exception);
        verifyNoMoreInteractions(delegateCall);
    }

    @Test
    public void testRetryOther_notHandled() throws Exception {
        QosIoException exception = new QosIoException(QosException.retryOther(new URL("http://foo")), RESPONSE);
        assertThatThrownBy(() -> handler.handle(call, exception).get(1, TimeUnit.SECONDS))
                .hasMessage("java.io.IOException: Internal error, did not expect to handle "
                        + "RetryOther exception in handler")
                .hasCauseInstanceOf(IOException.class);
        verifyNoMoreInteractions(delegateCall);
    }

    @Test
    public void testUnavailable_doesNotScheduleWhenBackoffIsEmpty() throws Exception {
        when(backoff.nextBackoff()).thenReturn(NO_BACKOFF);
        QosIoException exception = new QosIoException(QosException.unavailable(), RESPONSE);
        assertThatThrownBy(() -> handler.handle(call, exception).get(1, TimeUnit.SECONDS))
                .hasCause(exception);
        verifyNoMoreInteractions(delegateCall);
    }

    @Test
    public void testUnavailable_reschedulesByBackoffDuration() throws Exception {
        when(backoff.nextBackoff()).thenReturn(BACKOFF_10SECS);
        QosIoException exception = new QosIoException(QosException.unavailable(), RESPONSE);
        ListenableFuture<Response> response = handler.handle(call, exception);

        scheduler.tick(5, TimeUnit.SECONDS);
        verifyNoMoreInteractions(callFactory);
        assertThat(response.isDone()).isFalse();
        assertThat(response.isCancelled()).isFalse();

        scheduler.tick(10, TimeUnit.SECONDS);
        verify(callFactory).newCall(any());
        assertThat(response.isDone()).isTrue();
        assertThat(response.get(1, TimeUnit.SECONDS)).isEqualTo(CLONED_RESPONSE);
    }
}
