/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.remoting3.clients.ClientConfiguration;
import org.junit.Test;

public final class JaxRsClientConfigTest extends TestBase {
    @Test
    public void testRetries_notSupported() throws Exception {
        try {
            ClientConfiguration config = ClientConfiguration.builder()
                    .from(createTestConfig("uri"))
                    .maxNumRetries(1)
                    .build();
            JaxRsClient.create(TestService.class, "agent", config);
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), is("Connection-level retries are not supported by JaxRsClient"));
        }
    }
}
