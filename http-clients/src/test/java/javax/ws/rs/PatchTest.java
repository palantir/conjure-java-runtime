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

package javax.ws.rs;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.palantir.remoting.http.FeignClients;
import com.palantir.remoting.http.ObjectMappers;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.IOException;
import java.util.Map;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class PatchTest {
    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TestPatchServer.class,
            "src/test/resources/test-server.yml");

    private TestPatchServer.TestService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = FeignClients.standard().createProxy(Optional.<SSLSocketFactory>absent(), endpointUri,
                TestPatchServer.TestService.class);
    }

    @Test
    public void testPatch() throws IOException {
        assertThat(
                service.getService(),
                is((Map<String, String>) ImmutableMap.of("name", "originalName")));

        JsonNode jsonNode = ObjectMappers.guavaJdk7().readTree(
                "[ { \"op\": \"replace\", \"path\": \"/name\", \"value\": \"patchedName\" } ]");
        JsonPatch jsonPatch = JsonPatch.fromJson(jsonNode);

        assertThat(
                service.patchService(jsonPatch),
                is((Map<String, String>) ImmutableMap.of("name", "patchedName")));
    }
}
