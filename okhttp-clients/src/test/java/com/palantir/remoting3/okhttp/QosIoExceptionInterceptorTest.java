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
import static org.mockito.Mockito.when;

import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class QosIoExceptionInterceptorTest extends TestBase {

    private static final Request REQUEST = new Request.Builder().url("http://127.0.0.1").build();
    private static final Interceptor INTERCEPTOR = QosIoExceptionInterceptor.INSTANCE;

    @Mock
    private Interceptor.Chain chain;

    @Before
    public void before() throws Exception {
        when(chain.request()).thenReturn(REQUEST);
    }

    @Test
    public void test308WithoutLocation() throws Exception {
        Response response = responseWithCode(REQUEST, 308);
        when(chain.proceed(REQUEST)).thenReturn(response);

        assertThatThrownBy(() -> INTERCEPTOR.intercept(chain))
                .isInstanceOf(IOException.class)
                .hasMessageStartingWith("Retrieved HTTP status code 308 without Location header, cannot perform "
                        + "redirect. This appears to be a server-side protocol violation.");
    }

    @Test
    public void test308WithLocation() throws Exception {
        URL url = new URL("http://127.0.0.1");
        Response response = responseWithCode(REQUEST, 308).newBuilder().header("Location", url.toString()).build();
        when(chain.proceed(REQUEST)).thenReturn(response);

        assertThatThrownBy(() -> INTERCEPTOR.intercept(chain))
                .isInstanceOfSatisfying(QosIoException.class, e -> {
                    assertThat(e.getResponse()).isEqualTo(response);
                    assertThat(e.getQosException()).isInstanceOfSatisfying(QosException.RetryOther.class,
                            retryOtherException ->
                                    assertThat(retryOtherException.getRedirectTo()).isEqualTo(url));
                });
    }

    @Test
    public void test429WithoutRetryAfter() throws Exception {
        Response response = responseWithCode(REQUEST, 429);
        when(chain.proceed(REQUEST)).thenReturn(response);

        assertThatThrownBy(() -> INTERCEPTOR.intercept(chain))
                .isInstanceOfSatisfying(QosIoException.class, e -> {
                    assertThat(e.getResponse()).isEqualTo(response);
                    assertThat(e.getQosException()).isInstanceOfSatisfying(QosException.Throttle.class,
                            throttleException -> assertThat(throttleException.getRetryAfter()).isEmpty());
                });
    }

    @Test
    public void test429WithRetryAfter() throws Exception {
        Response response = responseWithCode(REQUEST, 429).newBuilder().header("Retry-After", "120").build();
        when(chain.proceed(REQUEST)).thenReturn(response);

        assertThatThrownBy(() -> INTERCEPTOR.intercept(chain))
                .isInstanceOfSatisfying(QosIoException.class, e -> {
                    assertThat(e.getResponse()).isEqualTo(response);
                    assertThat(e.getQosException()).isInstanceOfSatisfying(QosException.Throttle.class,
                            throttleException ->
                                    assertThat(throttleException.getRetryAfter()).contains(Duration.ofMinutes(2)));
                });
    }

    @Test
    public void test503() throws Exception {
        Response response = responseWithCode(REQUEST, 503);
        when(chain.proceed(REQUEST)).thenReturn(response);

        assertThatThrownBy(() -> INTERCEPTOR.intercept(chain))
                .isInstanceOfSatisfying(QosIoException.class, e -> {
                    assertThat(e.getResponse()).isEqualTo(response);
                    assertThat(e.getQosException()).isInstanceOf(QosException.Unavailable.class);
                });
    }
}
