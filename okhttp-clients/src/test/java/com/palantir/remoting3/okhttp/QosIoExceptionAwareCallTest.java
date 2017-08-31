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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.common.util.concurrent.Futures;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class QosIoExceptionAwareCallTest extends TestBase {

    @Mock
    private Call delegate;
    @Mock
    private QosIoExceptionHandler handler;

    private static final Request REQUEST = new Request.Builder().url("http://foo").build();
    private static final Response RESPONSE = responseWithCode(REQUEST, 200);

    @Test
    public void executionWithoutError_propagatesReturnValue() throws Exception {
        QosIoExceptionAwareCall call = new QosIoExceptionAwareCall(delegate, handler);
        when(delegate.execute()).thenReturn(RESPONSE);
        assertThat(call.execute()).isEqualTo(RESPONSE);
        verify(delegate).execute();
        verifyNoMoreInteractions(handler);
    }

    @Test
    public void executionWithQosIoException_invokesHandler() throws Exception {
        QosIoExceptionAwareCall call = new QosIoExceptionAwareCall(delegate, handler);
        QosIoException qosIoException = new QosIoException(QosException.unavailable(), null);
        when(delegate.execute()).thenThrow(qosIoException);
        when(handler.handle(any(), any())).thenReturn(Futures.immediateFuture(RESPONSE));

        assertThat(call.execute()).isEqualTo(RESPONSE);
        verify(delegate).execute();
        verify(handler).handle(call, qosIoException);
    }

    @Test
    public void executionWithIoException_propagatesException() throws Exception {
        QosIoExceptionAwareCall call = new QosIoExceptionAwareCall(delegate, handler);
        IOException ioException = new IOException("Foo");
        when(delegate.execute()).thenThrow(ioException);

        assertThatThrownBy(call::execute).isEqualTo(ioException);
    }
}
