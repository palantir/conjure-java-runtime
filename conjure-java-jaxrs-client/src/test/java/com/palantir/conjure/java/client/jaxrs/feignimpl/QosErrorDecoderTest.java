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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.api.errors.QosException;
import feign.Request;
import feign.Request.Body;
import feign.Request.HttpMethod;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.ws.rs.core.HttpHeaders;
import org.junit.Before;
import org.junit.Test;

public final class QosErrorDecoderTest {

    private static final Request REQUEST = Request.create(
            HttpMethod.GET,
            "",
            ImmutableMap.of(),
            Body.empty(),
            null);
    private static final String methodKey = "method";

    private QosErrorDecoder decoder;

    @Before
    public void before() {
        decoder = new QosErrorDecoder(new ErrorDecoder.Default());
    }

    @Test
    public void http_429_throw_qos_throttle() {
        Response response = Response.builder()
                .request(REQUEST)
                .status(429)
                .reason("too many requests")
                .body("", StandardCharsets.UTF_8)
                .build();

        assertThat(decoder.decode(methodKey, response))
                .isInstanceOfSatisfying(
                        QosException.Throttle.class,
                        e -> assertThat(e.getRetryAfter()).isEmpty());
    }

    @Test
    public void http_429_throw_qos_throttle_with_retry_after() {
        Response response = Response.builder()
                .request(REQUEST)
                .status(429)
                .reason("too many requests")
                .headers(ImmutableMap.of(
                        HttpHeaders.RETRY_AFTER,
                        ImmutableList.of("5")))
                .body("", StandardCharsets.UTF_8)
                .build();

        assertThat(decoder.decode(methodKey, response))
                .isInstanceOfSatisfying(
                        QosException.Throttle.class,
                        e -> assertThat(e.getRetryAfter()).contains(Duration.ofSeconds(5)));
    }

    @Test
    public void http_503_throw_qos_unavailable() {
        Response response = Response.builder()
                .request(REQUEST)
                .status(503)
                .reason("too many requests")
                .body("", StandardCharsets.UTF_8)
                .build();

        assertThat(decoder.decode(methodKey, response))
                .isInstanceOf(QosException.Unavailable.class);
    }

}
