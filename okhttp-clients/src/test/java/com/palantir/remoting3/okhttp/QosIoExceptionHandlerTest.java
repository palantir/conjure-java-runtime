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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import org.assertj.core.api.AbstractThrowableAssert;
import org.assertj.core.api.AssertionsForClassTypes;
import org.jmock.lib.concurrent.DeterministicScheduler;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(Parameterized.class)
public final class QosIoExceptionHandlerTest extends TestBase {

    @Parameterized.Parameters
    public static Collection<Boolean> clusters() {
        return ImmutableList.of(true, false);
    }

    private static final Request REQUEST = new Request.Builder().url("http://foo").build();
    private static final Response RESPONSE = responseWithCode(REQUEST, 101 /* unused */);
    private static final Response CLONED_RESPONSE = responseWithCode(REQUEST, 102 /* unused */);
    private static final Optional<Duration> BACKOFF_10SECS = Optional.of(Duration.ofSeconds(10));
    private static final Optional<Duration> NO_BACKOFF = Optional.empty();

    @Mock
    private BackoffStrategy backoff;
    @Mock
    private BackoffSleeper sleeper;
    @Mock
    private Call delegateCall;
    @Mock
    private Call clonedDelegateCall;

    private QosIoExceptionHandlerImpl handler;
    private QosIoExceptionAwareCall call;
    private DeterministicScheduler scheduler = new DeterministicScheduler();

    private Adapter adapter;

    public QosIoExceptionHandlerTest(boolean async) {
        adapter = async ? new AsyncAdapter() : new BlockingAdapter();
    }

    @Before
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        handler = new QosIoExceptionHandlerImpl(backoff, sleeper, Executors.newSingleThreadScheduledExecutor());
        call = new QosIoExceptionAwareCall(delegateCall, handler);
        when(call.clone()).thenReturn(clonedDelegateCall);
        when(delegateCall.execute()).thenReturn(RESPONSE);
        when(clonedDelegateCall.execute()).thenReturn(CLONED_RESPONSE);
        doAnswer(args -> {
            Callback callback = args.getArgumentAt(0, Callback.class);
            callback.onResponse(delegateCall, CLONED_RESPONSE);
            return null;
        }).when(clonedDelegateCall).enqueue(any());
    }

    @Test
    public void testThrottle_withRetryAfter_reschedules() throws Exception {
        QosIoException exception = new QosIoException(QosException.throttle(BACKOFF_10SECS.get()), RESPONSE);

        verifyBackoffAndRetry(exception);
    }

    @Test
    public void testThrottle_withoutRetryAfter_reschedulesByBackoffDuration() throws Exception {
        when(backoff.nextBackoff()).thenReturn(BACKOFF_10SECS);
        QosIoException exception = new QosIoException(QosException.throttle(BACKOFF_10SECS.get()), RESPONSE);

        verifyBackoffAndRetry(exception);
    }

    @Test
    public void testThrottle_withoutRetryAfter_failsIfBackoffIsEmpty() throws Exception {
        when(backoff.nextBackoff()).thenReturn(NO_BACKOFF);
        QosIoException exception = new QosIoException(QosException.throttle(), RESPONSE);

        verifyNoRetryAndThrow(exception).isEqualTo(exception);
    }

    @Test
    public void testRetryOther_notHandled() throws Exception {
        QosIoException exception = new QosIoException(QosException.retryOther(new URL("http://foo")), RESPONSE);

        verifyNoRetryAndThrow(exception)
                .hasMessageContaining("did not expect to handle RetryOther exception in handler")
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testUnavailable_doesNotScheduleWhenBackoffIsEmpty() throws Exception {
        when(backoff.nextBackoff()).thenReturn(NO_BACKOFF);
        QosIoException exception = new QosIoException(QosException.unavailable(), RESPONSE);

        verifyNoRetryAndThrow(exception).isEqualTo(exception);
    }

    @Test
    public void testUnavailable_reschedulesByBackoffDuration() throws Exception {
        when(backoff.nextBackoff()).thenReturn(BACKOFF_10SECS);
        QosIoException exception = new QosIoException(QosException.unavailable(), RESPONSE);

        verifyBackoffAndRetry(exception);
    }

    private void verifyBackoffAndRetry(QosIoException exception) throws Exception {
        adapter.verifyBackoffAndRetry(exception);
    }

    private AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception)
            throws Exception {
        return adapter.verifyNoRetryAndThrow(exception);
    }

    private interface Adapter {

        void verifyBackoffAndRetry(QosIoException exception) throws Exception;

        AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception);

    }

    class BlockingAdapter implements Adapter {

        @Override
        public void verifyBackoffAndRetry(QosIoException exception) throws Exception {
            Response response = handler.handle(call, exception);

            verify(sleeper).sleepForBackoff(BACKOFF_10SECS.get());
            verify(delegateCall).clone();
            verifyNoMoreInteractions(delegateCall);
            assertThat(response).isEqualTo(CLONED_RESPONSE);
        }

        @Override
        public AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception) {
            AbstractThrowableAssert<?, ? extends Throwable> thrown =
                    assertThatThrownBy(() -> handler.handle(call, exception));

            verifyZeroInteractions(sleeper);
            verifyZeroInteractions(delegateCall);

            return thrown;
        }
    }

    class AsyncAdapter implements Adapter {

        @Override
        public void verifyBackoffAndRetry(QosIoException exception) {
            FutureCallback response = new FutureCallback();
            handler.handleAsync(call, exception, response);

            scheduler.tick(5, TimeUnit.SECONDS);
            verifyNoMoreInteractions(delegateCall);
            assertThat(response.isDone()).isFalse();
            assertThat(response.isCancelled()).isFalse();

            scheduler.tick(10, TimeUnit.SECONDS);
            verify(delegateCall).clone();
            assertThat(response.isDone()).isTrue();
            assertThat(response.getNow(null)).isEqualTo(CLONED_RESPONSE);
        }

        @Override
        public AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception) {
            FutureCallback response = new FutureCallback();
            handler.handleAsync(call, exception, response);
            assertThat(response.isDone()).isTrue();

            verifyZeroInteractions(scheduler);
            verifyZeroInteractions(delegateCall);

            return AssertionsForClassTypes.assertThat(response.getException());
        }
    }

    private class FutureCallback extends CompletableFuture<Response> implements Callback {

        @Override
        public void onFailure(Call call, IOException e) {
            completeExceptionally(e);
        }

        @Override
        public void onResponse(Call call, Response response) throws IOException {
            complete(response);
        }

        @Nullable
        public Throwable getException() {
            try {
                getNow(null);
                throw new IllegalStateException("Callback did not receive an exception");
            } catch (CompletionException ex) {
                return ex.getCause();
            }
        }
    }

}
