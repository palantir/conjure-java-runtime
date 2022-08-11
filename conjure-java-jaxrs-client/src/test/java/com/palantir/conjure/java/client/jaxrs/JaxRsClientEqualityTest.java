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

package com.palantir.conjure.java.client.jaxrs;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import org.junit.jupiter.api.Test;

public final class JaxRsClientEqualityTest extends TestBase {

    @Test
    public void assertEqualsSelf() {
        TestService instance = newTestServiceClient(8123);
        assertThat(instance).isEqualTo(instance);
    }

    @Test
    public void assertNotEqualsDifferentPort() {
        assertThat(newTestServiceClient(1234)).isNotEqualTo(newTestServiceClient(4321));
    }

    @Test
    public void assertNotEqualsSamePort() {
        int port = 1234;
        assertThat(newTestServiceClient(port))
                .as("The factory doesn't have a cache, these instances are different")
                .isNotEqualTo(newTestServiceClient(port));
    }

    private TestService newTestServiceClient(int port) {
        return JaxRsClient.create(
                TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                ClientConfiguration.builder()
                        .from(createTestConfig("http://localhost:" + port))
                        .maxNumRetries(1)
                        .build());
    }
}
