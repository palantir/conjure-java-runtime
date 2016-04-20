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

package com.palantir.config.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.tokens.auth.BearerToken;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import org.junit.Test;

/**
 * Tests for {@link ServiceDiscoveryConfiguration}.
 */
public final class ServiceDiscoveryConfigurationTests {

    private final ObjectMapper mapper = Jackson.newObjectMapper(new YAMLFactory());

    @Test
    public void testDeserializationWithoutFallbackProperites() throws IOException {
        URL resource = Resources.getResource("configs/discovery-config-without-fallback.yml");
        ServiceDiscoveryConfiguration discoveryConfig =
                mapper.readValue(resource.openStream(), ServiceDiscoveryConfiguration.class);

        // testing fallback properties
        assertFalse(discoveryConfig.defaultSecurity().isPresent());
        assertFalse(discoveryConfig.defaultApiToken().isPresent());

        // testing service api token property
        // 1) with token
        // 2) without token
        assertEquals(BearerToken.valueOf("service1ApiToken"), discoveryConfig.getApiToken("service1").get());
        assertEquals(BearerToken.valueOf("service2ApiToken"), discoveryConfig.getApiToken("service2").get());
        assertEquals(ImmutableList.of("https://some.internal.url:8443/thirdservice/api"), discoveryConfig.getUris("service3"));
        assertFalse(discoveryConfig.getApiToken("service3").isPresent());

        // testing getter for services that don't exist
        try {
            discoveryConfig.getApiToken("serviceThatDoesntExist");
            fail();
        } catch (RuntimeException e) {
            assertEquals("Unable to find the configuration for serviceThatDoesntExist.", e.getMessage());
        }
    }

    @Test
    public void testDeserializationWithFallbackProperites() throws IOException {
        URL resource = Resources.getResource("configs/discovery-config-with-fallback.yml");
        ServiceDiscoveryConfiguration discoveryConfig =
                mapper.readValue(resource.openStream(), ServiceDiscoveryConfiguration.class);

        // testing fallback properties
        assertTrue(discoveryConfig.defaultSecurity().isPresent());
        assertEquals(BearerToken.valueOf("defaultApiToken"), discoveryConfig.defaultApiToken().get());

        // testing service api token property
        // 1) with token
        // 2) without token
        assertEquals(BearerToken.valueOf("service1ApiToken"), discoveryConfig.getApiToken("service1").get());
        assertEquals(BearerToken.valueOf("defaultApiToken"), discoveryConfig.getApiToken("service3").get());
        assertEquals(BearerToken.valueOf("defaultApiToken"),
                discoveryConfig.getServices().get("service3").apiToken().get());
    }

    @Test
    public void testBuilder() {
        BearerToken defaultApiToken = BearerToken.valueOf("someToken");
        SslConfiguration security = SslConfiguration.of(mock(Path.class));

        ServiceConfiguration service = ServiceConfiguration.builder()
                .security(security)
                .uris(ImmutableList.of("https://localhost:8443"))
                .build();
        ServiceDiscoveryConfiguration services = ServiceDiscoveryConfiguration.builder()
                .defaultApiToken(defaultApiToken)
                .originalServices(ImmutableMap.of("service1", service))
                .build();

        assertEquals(defaultApiToken, services.defaultApiToken().get());
        assertEquals(defaultApiToken, services.getApiToken("service1").get());
        assertEquals(defaultApiToken, services.getServices().get("service1").apiToken().get());
        assertFalse(services.defaultSecurity().isPresent());
        assertEquals(security, services.getSecurity("service1").get());
        assertEquals(security, services.getServices().get("service1").security().get());
    }
}
