/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.logsafe.exceptions.SafeIllegalArgumentException;
import org.junit.jupiter.api.Test;

public final class JaxRsClientAnnotationVerificationTest extends TestBase {

    @Test
    public void can_create_client_with_no_annotations() {
        createClient(TestService.class);
    }

    @Test
    public void can_create_client_with_client_annotation() {
        createClient(OnlyClientAnnotation.class);
    }

    @Test
    public void can_create_client_with_client_and_server_annotation() {
        createClient(ClientAndServerAnnotation.class);
    }

    @Test
    public void fails_creating_client_with_only_server_annotation() {
        assertThatThrownBy(() -> createClient(OnlyServerAnnotation.class))
                .isInstanceOf(SafeIllegalArgumentException.class)
                .hasMessageContaining("Service class should not be used as a client");
    }

    @com.palantir.conjure.java.annotations.JaxRsClient
    interface OnlyClientAnnotation extends TestService {}

    @com.palantir.conjure.java.annotations.JaxRsServer
    interface OnlyServerAnnotation extends TestService {}

    @com.palantir.conjure.java.annotations.JaxRsServer
    @com.palantir.conjure.java.annotations.JaxRsClient
    interface ClientAndServerAnnotation extends TestService {}

    private void createClient(Class<?> serviceClass) {
        JaxRsClient.create(serviceClass, AGENT, new HostMetricsRegistry(), createTestConfig("http://localhost:4321"));
    }
}
