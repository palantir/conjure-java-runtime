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

package com.palantir.remoting.jaxrs;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.palantir.remoting.config.service.ServiceConfiguration;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;

public final class Jackson24Test {

    @Rule
    public final MockWebServer server = new MockWebServer();

    // Note that the Gradle setup forces Jackson 2.4 for this project.
    @Test
    public void test_CanBuildClientWithJackson24() throws JsonProcessingException {
        TestEchoService service = JaxRsClient.builder()
                .build(TestEchoService.class, "agent", "http://localhost:" + server.getPort());
        ServiceConfiguration config = ServiceConfiguration.builder().build();
        server.enqueue(new MockResponse().setBody(new String(new ObjectMapper().registerModule(new GuavaModule())
                .writeValueAsBytes(config), StandardCharsets.UTF_8)));
        assertThat(service.echo(config), is(config));
    }

    @Path("/")
    public interface TestEchoService {
        @POST
        @Path("/echo")
        ServiceConfiguration echo(ServiceConfiguration value);
    }
}
