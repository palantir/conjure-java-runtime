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

import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Uninterruptibles;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import org.junit.Test;

public class InterruptibleCallTest {
    private final Call call = mock(Call.class);
    private final InterruptibleCall interruptibleCall = new InterruptibleCall(call);

    @Test
    public void when_execute_is_called_it_should_execute_the_underlying_call() throws IOException {
        interruptibleCall.execute();
        verify(call).execute();
        verifyNoMoreInteractions(call);
    }

    @Test(timeout = 10_000)
    public void when_execute_is_called_and_the_thread_interrupted_the_underlying_call_should_be_cancelled()
            throws IOException, InterruptedException {

        CountDownLatch underlyingExecuteCalled = new CountDownLatch(1);

        when(call.execute()).thenAnswer(invocation -> {
            underlyingExecuteCalled.countDown();
            Thread.sleep(9999999999L);
            return null;
        });

        Thread thread = new Thread(() -> {
            try {
                interruptibleCall.execute();
            } catch (IOException e) {
                throw new RuntimeException();
            }
        });

        thread.start();

        underlyingExecuteCalled.await();
        thread.interrupt();

        thread.join();
        verify(call).cancel();
    }

    @Test
    public void when_enqueue_is_called_and_the_thread_interrupted_the_underlying_call_should_be_cancelled()
            throws InterruptedException {
        CountDownLatch underlyingEnqueueCalled = new CountDownLatch(1);

        doAnswer(invocation -> {
            underlyingEnqueueCalled.countDown();
            return null;
        }).when(call).enqueue(any());

        Thread thread = new Thread(() -> {
            interruptibleCall.enqueue(new Callback() {
                @Override
                public void onFailure(Call originalCall, IOException e) {
                    fail("Call should be cancelled, not fail");
                }

                @Override
                public void onResponse(Call originalCall, Response response) throws IOException {
                    fail("Call should be cancelled, not get a response");
                }
            });
        });

        thread.start();

        underlyingEnqueueCalled.await();
        thread.interrupt();

        thread.join();

        verify(call).cancel();
    }
}