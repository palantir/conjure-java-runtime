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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.undertest.UndertowServerExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Tests that nulls are correctly deserialized into empty collections. */
public final class CollectionDefaultDecodingTest extends TestBase {

    @RegisterExtension
    public static final UndertowServerExtension undertow = Java8TestServer.createUndertow();

    private Java8TestServer.TestService service;

    @BeforeEach
    public void before() {
        String endpointUri = "http://localhost:" + undertow.getLocalPort();
        service = JaxRsClient.create(
                Java8TestServer.TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
    }

    @Test
    public void testList() {
        assertThat(service.getNullList()).isEmpty();
    }

    @Test
    public void testSet() {
        assertThat(service.getNullSet()).isEmpty();
    }

    @Test
    public void testMap() {
        assertThat(service.getNullMap()).isEmpty();
    }
}
