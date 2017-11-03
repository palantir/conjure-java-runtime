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
import static org.mockito.Mockito.times;
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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
import org.mockito.stubbing.OngoingStubbing;

@RunWith(Parameterized.class)
public final class QosAwareCallRetryerTest extends TestBase {

    @Parameterized.Parameters
    public static Collection<Boolean> clusters() {
        return ImmutableList.of(false, true);
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

    private QosAwareCallRetryer retrier;
    private RetryingCall call;
    private DeterministicScheduler scheduler = new DeterministicScheduler();

    private RetryVerifier verifier;

    public QosAwareCallRetryerTest(boolean async) {
        verifier = async ? new AsyncCallVerifier() : new BlockingCallVerifier();
    }

    @Before
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);

        retrier = new QosAwareCallRetryer(backoff, sleeper, scheduler);
        call = new RetryingCall(delegateCall, retrier);
        when(delegateCall.clone()).thenReturn(clonedDelegateCall);
        when(clonedDelegateCall.clone()).thenReturn(clonedDelegateCall);
        when(backoff.nextBackoff()).thenReturn(BACKOFF_10SECS);
    }

    @Test
    public void testThrottle_withRetryAfter_reschedules() throws Exception {
        QosIoException exception = new QosIoException(QosException.throttle(BACKOFF_10SECS.get()), RESPONSE);

        verifyBackoffAndRetry(exception, 1);
    }

    @Test
    public void testThrottle_withoutRetryAfter_reschedulesByBackoffDuration() throws Exception {
        when(backoff.nextBackoff()).thenReturn(BACKOFF_10SECS);
        QosIoException exception = new QosIoException(QosException.throttle(BACKOFF_10SECS.get()), RESPONSE);

        verifyBackoffAndRetry(exception, 1);
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
                .satisfies(ex -> exceptionOrCauseHasMessage(ex, "did not expect to backoff for RetryOther"));
    }

    @Test
    public void test_UnexpectedExceptionDuringRetry_isPropagated() throws Exception {
        RuntimeException unexpected = new RuntimeException("foo");
        when(backoff.nextBackoff()).thenThrow(unexpected);
        QosIoException exception = new QosIoException(QosException.throttle(), RESPONSE);

        verifyNoRetryAndThrow(exception)
                .satisfies(ex -> exceptionOrCauseHasMessage(ex, "foo"));
    }

    private void exceptionOrCauseHasMessage(Throwable ex, String message) {
        assertThat(ex.getMessage().contains(message) || ex.getCause().getMessage().contains(message)).isTrue();
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

        verifyBackoffAndRetry(exception, 1);
    }

    @Test
    public void test_doesNotRetryIfNoExceptionIsThrown() throws Exception {
        verifyBackoffAndRetry(new IOException(), 0);
    }

    @Test
    public void testUnavailable_reschedulesByBackoffDuration_manyRetries() throws Exception {
        when(backoff.nextBackoff()).thenReturn(BACKOFF_10SECS);
        QosIoException exception = new QosIoException(QosException.unavailable(), RESPONSE);

        verifyBackoffAndRetry(exception, 100);
    }

    private void verifyBackoffAndRetry(IOException exception, int numRetries) throws Exception {
        stubResponseAfterThrowing(exception, numRetries);
        verifier.verifyBackoffAndRetry(exception, numRetries);
    }

    AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception)
            throws Exception {
        stubResponseAfterThrowing(exception, 1);
        return verifier.verifyNoRetryAndThrow(exception);
    }

    private interface RetryVerifier {

        void verifyBackoffAndRetry(IOException exception, int numRetries) throws Exception;

        AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception)
                throws Exception;

    }

    class BlockingCallVerifier implements RetryVerifier {

        @Override
        public void verifyBackoffAndRetry(IOException exception, int numRetries) throws Exception {
            Response response = call.execute();

            verify(sleeper, times(numRetries)).sleepForBackoff(BACKOFF_10SECS.get());
            verify(delegateCall, times(1)).execute();

            if (numRetries > 0) {
                verify(delegateCall, times(1)).clone();
                verify(clonedDelegateCall, times(numRetries)).execute();
                verify(clonedDelegateCall, times(numRetries - 1)).clone();
            }

            verifyNoMoreInteractions(delegateCall, clonedDelegateCall);

            assertThat(response).isEqualTo(numRetries > 0 ? CLONED_RESPONSE : RESPONSE);
        }

        @Override
        public AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception)
                throws Exception {
            AbstractThrowableAssert<?, ? extends Throwable> thrown =
                    assertThatThrownBy(() -> retrier.executeWithRetry(call));

            verify(delegateCall).execute();
            verifyZeroInteractions(sleeper);

            verifyNoMoreInteractions(delegateCall, clonedDelegateCall);

            return thrown;
        }
    }

    class AsyncCallVerifier implements RetryVerifier {

        @Override
        public void verifyBackoffAndRetry(IOException exception, int numRetries) {
            FutureCallback response = new FutureCallback();
            call.enqueue(response);

            if (numRetries > 0) {
                scheduler.tick(numRetries * 10 - 1, TimeUnit.SECONDS);
                assertThat(response.isDone()).isFalse();
                assertThat(response.isCancelled()).isFalse();
            }

            scheduler.tick(1, TimeUnit.NANOSECONDS);
            scheduler.tick(10 * numRetries, TimeUnit.SECONDS);
            assertThat(response.isDone()).isTrue();
            assertThat(response.getNow(null)).isEqualTo(numRetries > 0 ? CLONED_RESPONSE : RESPONSE);

            verify(delegateCall, times(1)).enqueue(any());
            verify(clonedDelegateCall, times(numRetries)).enqueue(any());

            if (numRetries > 0) {
                verify(delegateCall, times(1)).clone();
                verify(clonedDelegateCall, times(numRetries - 1)).clone();
            }

            verifyNoMoreInteractions(delegateCall, clonedDelegateCall);
        }

        @Override
        public AbstractThrowableAssert<?, ? extends Throwable> verifyNoRetryAndThrow(QosIoException exception) {
            FutureCallback response = new FutureCallback();
            call.enqueue(response);
            scheduler.tick(100, TimeUnit.SECONDS);
            assertThat(response.isDone()).isTrue();

            verify(delegateCall, times(1)).enqueue(any());

            verifyNoMoreInteractions(clonedDelegateCall);

            return AssertionsForClassTypes.assertThat(response.getException());
        }
    }

    private void stubResponseAfterThrowing(IOException exception, int numThrows) throws Exception {
        if (numThrows == 0) {
            // blocking
            when(delegateCall.execute()).thenReturn(RESPONSE);

            // async
            doAnswer(inv -> {
                Callback callback = inv.getArgumentAt(0, Callback.class);
                callback.onResponse(delegateCall, RESPONSE);
                return null;
            }).when(delegateCall).enqueue(any());

            return;
        }

        // blocking
        when(delegateCall.execute()).thenThrow(exception);
        OngoingStubbing<Response> stubbing = when(clonedDelegateCall.execute());
        for (int i = 1; i < numThrows; i++) {
            stubbing = stubbing.thenThrow(exception);
        }
        stubbing = stubbing.thenReturn(CLONED_RESPONSE);

        // async
        doAnswer(inv -> {
            Callback callback = inv.getArgumentAt(0, Callback.class);
            callback.onFailure(delegateCall, exception);
            return null;
        }).when(delegateCall).enqueue(any());

        AtomicLong numThrown = new AtomicLong(1);
        doAnswer(inv -> {
            Callback callback = inv.getArgumentAt(0, Callback.class);

            if (numThrown.incrementAndGet() <= numThrows) {
                callback.onFailure(clonedDelegateCall, exception);
            } else {
                callback.onResponse(clonedDelegateCall, CLONED_RESPONSE);
            }
            return null;
        }).when(clonedDelegateCall).enqueue(any());

    }

    private class FutureCallback extends CompletableFuture<Response> implements Callback {

        @Override
        public void onFailure(Call ignored, IOException ex) {
            completeExceptionally(ex);
        }

        @Override
        public void onResponse(Call ignored, Response response) throws IOException {
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
