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

package com.palantir.remoting3.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class ContextAwareTaskExecutionTest {
    private static final String TEST_CONTEXT_KEY = "contextKey";
    private static final String EXCLUDED_CONTEXT_KEY = "excluded";
    public static final String EXCLUDED_VALUE = "partial-exclude";

    @Mock
    private Contextual original;
    @Mock
    private Contextual copy;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(original.taskCopy()).thenReturn(copy);

        RequestContext.set(TEST_CONTEXT_KEY, original);
        RequestContext.setString(EXCLUDED_CONTEXT_KEY, EXCLUDED_VALUE);
    }

    @After
    public void after() {
        RequestContext.getAndClear();
    }

    @Test
    public void testExecutorServiceWrapsCallables() throws Exception {
        ExecutorService wrappedService = ContextAwareTaskExecution.wrap(Executors.newSingleThreadExecutor());

        wrappedService.submit(contextExpectingCallable()).get();
        wrappedService.submit(contextExpectingRunnable()).get();

        // Includes additional keys
        RequestContext.setBoolean("additionalKey", true);
        wrappedService.submit(contextExpectingCallable("additionalKey")).get();
        wrappedService.submit(contextExpectingRunnable("additionalKey")).get();
    }

    @Test
    public void testScheduledExecutorServiceWrapsCallables() throws Exception {
        ScheduledExecutorService wrappedService = ContextAwareTaskExecution.wrap(
                Executors.newSingleThreadScheduledExecutor());

        wrappedService.schedule(contextExpectingCallable(), 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(contextExpectingRunnable(), 0, TimeUnit.SECONDS).get();

        // Includes additional keys
        RequestContext.setBoolean("additionalKey", true);
        wrappedService.schedule(contextExpectingCallable("additionalKey"), 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(contextExpectingRunnable("additionalKey"), 0, TimeUnit.SECONDS).get();
    }

    @Test
    public void testScheduledExecutorServiceWrapsCallablesWithNewContexts() throws Exception {
        ScheduledExecutorService wrappedService =
                ContextAwareTaskExecution.wrapWithNewContext(Executors.newSingleThreadScheduledExecutor());

        Callable<Void> callable = newContextExpectingCallable();
        Runnable runnable = newContextExpectingRunnable();

        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        // Still empty in subsequent calls
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        // Still empty after explicit variable resetting
        RequestContext.setString(TEST_CONTEXT_KEY, "newString");
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();
    }

    @Test
    public void testScheduledExecutorServiceWrapsCallablesWithPartialContexts() throws Exception {
        ScheduledExecutorService wrappedService = ContextAwareTaskExecution.wrapWithPartialContext(
                Executors.newSingleThreadScheduledExecutor(), EXCLUDED_CONTEXT_KEY);

        Callable<Void> callable = partialExpectingCallable();
        Runnable runnable = partialExpectingRunnable();

        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        // Still empty in subsequent calls
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();

        // Still empty after explicit variable resetting
        RequestContext.setString(EXCLUDED_CONTEXT_KEY, "newValue");
        wrappedService.schedule(callable, 0, TimeUnit.SECONDS).get();
        wrappedService.schedule(runnable, 0, TimeUnit.SECONDS).get();
    }

    @Test
    public void testWrappingRunnable_runnableContextIsIsolated() throws Exception {
        Runnable runnable = ContextAwareTaskExecution.wrap(() -> {
            RequestContext.remove(EXCLUDED_CONTEXT_KEY);
            RequestContext.setString(TEST_CONTEXT_KEY, "newValue");
            RequestContext.setInteger("newKey", 1);
        });

        runnable.run();
        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
        assertThat(RequestContext.getString(EXCLUDED_CONTEXT_KEY, "default")).contains(EXCLUDED_VALUE);
        assertThat(RequestContext.get("newKey")).isEmpty();
    }

    @Test
    public void testWrappingRunnable_contextStateIsCapturedAtConstructionTime() throws Exception {
        Runnable runnable = ContextAwareTaskExecution.wrap(contextExpectingRunnable());
        RequestContext.remove(TEST_CONTEXT_KEY);
        runnable.run();
    }

    @Test
    public void testWrappingCallable_callableContextIsIsolated() throws Exception {
        Callable<Void> callable = ContextAwareTaskExecution.wrap(() -> {
            RequestContext.remove(EXCLUDED_CONTEXT_KEY);
            RequestContext.setString(TEST_CONTEXT_KEY, "newValue");
            RequestContext.setInteger("newKey", 1);
            return null;
        });

        callable.call();
        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
        assertThat(RequestContext.getString(EXCLUDED_CONTEXT_KEY, "default")).contains(EXCLUDED_VALUE);
        assertThat(RequestContext.get("newKey")).isEmpty();
    }

    @Test
    public void testWrappingCallable_contextStateIsCapturedAtConstructionTime() throws Exception {
        Callable<Void> callable = ContextAwareTaskExecution.wrap(contextExpectingCallable());
        RequestContext.remove(TEST_CONTEXT_KEY);
        callable.call();
    }

    @Test
    public void testWrapCallableWithPartialContext_contextStateInsideCallableIsIsolated() throws Exception {
        Callable<Void> wrappedCallable = ContextAwareTaskExecution.wrapWithPartialContext(partialExpectingCallable(),
                EXCLUDED_CONTEXT_KEY);

        wrappedCallable.call();
        wrappedCallable.call();

        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
        assertThat(RequestContext.getString(EXCLUDED_CONTEXT_KEY, "default")).isEqualTo(EXCLUDED_VALUE);
    }

    @Test
    public void testWrapCallableWithPartialContext_contextStateRestoredWhenThrows() throws Exception {
        Callable<Void> rawCallable = () -> {
            partialExpectingRunnable().run();
            throw new IllegalStateException();
        };
        Callable<Void> wrappedCallable = ContextAwareTaskExecution.wrapWithPartialContext(rawCallable,
                EXCLUDED_CONTEXT_KEY);

        assertThatThrownBy(wrappedCallable::call).isInstanceOf(IllegalStateException.class);
        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
    }

    @Test
    public void testWrapRunnableWithPartialContext_contextStateInsideRunnableIsIsolated() throws Exception {
        Runnable wrappedRunnable = ContextAwareTaskExecution.wrapWithPartialContext(partialExpectingRunnable(),
                EXCLUDED_CONTEXT_KEY);

        wrappedRunnable.run();
        wrappedRunnable.run();

        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
    }

    @Test
    public void testWrapRunnableWithPartialContext_contextStateRestoredWhenThrows() throws Exception {
        Runnable rawRunnable = () -> {
            partialExpectingRunnable().run();
            throw new IllegalStateException();
        };
        Runnable wrappedRunnable = ContextAwareTaskExecution.wrapWithPartialContext(rawRunnable, EXCLUDED_CONTEXT_KEY);

        assertThatThrownBy(wrappedRunnable::run).isInstanceOf(IllegalStateException.class);
        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
    }

    @Test
    public void testWrapCallableWithNewContext_contextStateInsideCallableIsIsolated() throws Exception {
        Callable<Void> wrappedCallable = ContextAwareTaskExecution.wrapWithNewContext(newContextExpectingCallable());

        wrappedCallable.call();
        wrappedCallable.call();

        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
    }

    @Test
    public void testWrapCallableWithNewContext_contextStateRestoredWhenThrows() throws Exception {
        Callable<Void> rawCallable = () -> {
            newContextExpectingRunnable().run();
            throw new IllegalStateException();
        };
        Callable<Void> wrappedCallable = ContextAwareTaskExecution.wrapWithNewContext(rawCallable);

        assertThatThrownBy(wrappedCallable::call).isInstanceOf(IllegalStateException.class);
        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
    }

    @Test
    public void testWrapRunnableWithNewContext_contextStateInsideRunnableIsIsolated() throws Exception {
        Runnable wrappedRunnable = ContextAwareTaskExecution.wrapWithNewContext(newContextExpectingRunnable());

        wrappedRunnable.run();
        wrappedRunnable.run();

        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
    }

    @Test
    public void testWrapRunnableWithNewContext_contextStateRestoredWhenThrows() throws Exception {
        Runnable rawRunnable = () -> {
            newContextExpectingRunnable().run();
            throw new IllegalStateException();
        };
        Runnable wrappedRunnable = ContextAwareTaskExecution.wrapWithNewContext(rawRunnable);

        assertThatThrownBy(wrappedRunnable::run).isInstanceOf(IllegalStateException.class);
        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(original);
    }

    private Callable<Void> partialExpectingCallable() {
        return () -> {
            assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(copy);
            assertThat(RequestContext.get(EXCLUDED_CONTEXT_KEY)).isEmpty();
            RequestContext.setBoolean(EXCLUDED_CONTEXT_KEY, true);
            return null;
        };
    }

    private Runnable partialExpectingRunnable() {
        return () -> {
            assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(copy);
            assertThat(RequestContext.get(EXCLUDED_CONTEXT_KEY)).isEmpty();
            RequestContext.setBoolean(EXCLUDED_CONTEXT_KEY, true);
        };
    }

    private static Callable<Void> newContextExpectingCallable() {
        return () -> {
            assertThat(RequestContext.currentContext()).isEmpty();
            RequestContext.setString("someKey", "newValue");
            return null;
        };
    }

    private static Runnable newContextExpectingRunnable() {
        return () -> {
            assertThat(RequestContext.currentContext()).isEmpty();
            RequestContext.setString("someKey", "newValue");
        };
    }

    private Callable<Void> contextExpectingCallable(String... additionalKeys) {
        return () -> {
            assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(copy);
            assertThat(RequestContext.get(EXCLUDED_CONTEXT_KEY)).isNotEmpty();
            for (String key : additionalKeys) {
                assertThat(RequestContext.get(key)).isNotEmpty();
            }
            return null;
        };
    }

    private Runnable contextExpectingRunnable(String... additionalKeys) {
        return () -> {
            assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(copy);
            assertThat(RequestContext.get(EXCLUDED_CONTEXT_KEY)).isNotEmpty();
            for (String key : additionalKeys) {
                assertThat(RequestContext.get(key)).isNotEmpty();
            }
        };
    }
}
