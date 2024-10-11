/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.palantir.conjure.java.api.errors.QosException;
import com.palantir.conjure.java.api.errors.QosReason;
import com.palantir.conjure.java.api.errors.QosReason.DueTo;
import com.palantir.conjure.java.api.errors.QosReason.RetryHint;
import com.palantir.conjure.java.api.errors.QosReasons;
import feign.Response;
import feign.codec.ErrorDecoder;
import jakarta.ws.rs.core.HttpHeaders;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;

public final class QosErrorDecoderTest {

    private static final String methodKey = "method";

    static QosErrorDecoder decoder() {
        return new QosErrorDecoder(new ErrorDecoder.Default());
    }

    @Test
    public void http_429_throw_qos_throttle() {
        QosReason expected = QosReason.builder().reason("client-qos-response").build();
        Map<String, Collection<String>> headers = headersFor(expected);
        Response response = Response.create(429, "too many requests", headers, new byte[0]);
        assertThat(decoder().decode(methodKey, response))
                .isInstanceOfSatisfying(QosException.Throttle.class, throttle -> {
                    assertThat(throttle.getRetryAfter()).isEmpty();
                    assertThat(throttle.getReason()).isEqualTo(expected);
                });
    }

    @Test
    public void http_429_throw_qos_throttle_with_metadata() {
        QosReason expected = QosReason.builder()
                .reason("client-qos-response")
                .dueTo(DueTo.CUSTOM)
                .retryHint(RetryHint.DO_NOT_RETRY)
                .build();
        Map<String, Collection<String>> headers = headersFor(expected);
        Response response = Response.create(429, "too many requests", headers, new byte[0]);
        assertThat(decoder().decode(methodKey, response))
                .isInstanceOfSatisfying(QosException.Throttle.class, throttle -> {
                    assertThat(throttle.getRetryAfter()).isEmpty();
                    assertThat(throttle.getReason()).isEqualTo(expected);
                });
    }

    @Test
    public void http_429_throw_qos_throttle_with_retry_after() {
        Map<String, Collection<String>> headers = ImmutableMap.of(HttpHeaders.RETRY_AFTER, ImmutableList.of("5"));
        Response response = Response.create(429, "too many requests", headers, new byte[0]);
        assertThat(decoder().decode(methodKey, response))
                .isInstanceOfSatisfying(QosException.Throttle.class, e -> assertThat(e.getRetryAfter())
                        .contains(Duration.ofSeconds(5)));
    }

    @Test
    public void http_503_throw_qos_unavailable() {
        QosReason expected = QosReason.builder().reason("client-qos-response").build();
        Map<String, Collection<String>> headers = headersFor(expected);
        Response response = Response.create(503, "unavailable", headers, new byte[0]);
        assertThat(decoder().decode(methodKey, response))
                .isInstanceOfSatisfying(
                        QosException.Unavailable.class,
                        unavailable -> assertThat(unavailable.getReason()).isEqualTo(expected));
    }

    @Test
    public void http_503_throw_qos_unavailable_with_metadata() {
        QosReason expected = QosReason.builder()
                .reason("client-qos-response")
                .dueTo(DueTo.CUSTOM)
                .retryHint(RetryHint.DO_NOT_RETRY)
                .build();
        Map<String, Collection<String>> headers = headersFor(expected);
        Response response = Response.create(503, "unavailable", headers, new byte[0]);
        assertThat(decoder().decode(methodKey, response))
                .isInstanceOfSatisfying(
                        QosException.Unavailable.class,
                        unavailable -> assertThat(unavailable.getReason()).isEqualTo(expected));
    }

    private static Map<String, Collection<String>> headersFor(QosReason reason) {
        Multimap<String, String> headers = ArrayListMultimap.create();
        QosReasons.encodeToResponse(reason, headers, Multimap::put);
        return Multimaps.asMap(headers);
    }
}
