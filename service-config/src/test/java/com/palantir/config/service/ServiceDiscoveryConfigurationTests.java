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
        assertFalse(discoveryConfig.defaultConnectTimeout().isPresent());
        assertFalse(discoveryConfig.defaultReadTimeout().isPresent());
        assertFalse(discoveryConfig.defaultProxyConfiguration().isPresent());

        // testing service api token property
        // 1) with token
        // 2) without token
        assertEquals(BearerToken.valueOf("service1ApiToken"), discoveryConfig.getApiToken("service1").get());
        assertEquals(BearerToken.valueOf("service2ApiToken"), discoveryConfig.getApiToken("service2").get());
        assertEquals(ImmutableList.of("https://some.internal.url:8443/thirdservice/api"), discoveryConfig.getUris("service3"));
        assertEquals(Duration.minutes(1), discoveryConfig.getReadTimeout("service2").get());
        assertEquals(Duration.minutes(5), discoveryConfig.getConnectTimeout("service3").get());
        assertEquals(Duration.seconds(30), discoveryConfig.getReadTimeout("service3").get());
        assertFalse(discoveryConfig.getApiToken("service3").isPresent());
        assertEquals(ProxyConfiguration.of("squid:3128"), discoveryConfig.getProxyConfiguration("service3").get());
        assertFalse(discoveryConfig.getProxyConfiguration("service2").isPresent());

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
        assertTrue(discoveryConfig.defaultConnectTimeout().isPresent());
        assertEquals(Duration.seconds(45), discoveryConfig.defaultConnectTimeout().get());
        assertTrue(discoveryConfig.defaultConnectTimeout().isPresent());
        assertEquals(Duration.minutes(15), discoveryConfig.defaultReadTimeout().get());
        assertEquals(ProxyConfiguration.of("globalSquid:3128"), discoveryConfig.defaultProxyConfiguration().get());

        // testing service api token property
        // 1) with token
        // 2) without token
        assertEquals(BearerToken.valueOf("service1ApiToken"), discoveryConfig.getApiToken("service1").get());
        assertEquals(BearerToken.valueOf("defaultApiToken"), discoveryConfig.getApiToken("service3").get());
        assertEquals(BearerToken.valueOf("defaultApiToken"),
                discoveryConfig.getServices().get("service3").apiToken().get());
        assertEquals(ProxyConfiguration.of("globalSquid:3128"),
                discoveryConfig.getProxyConfiguration("service1").get());
        assertEquals(ProxyConfiguration.of("service3squid:3128"),
                discoveryConfig.getProxyConfiguration("service3").get());
    }

    @Test
    public void testIsServiceEnabled() throws IOException {
        URL resource = Resources.getResource("configs/discovery-config-with-empty-uri.yml");
        ServiceDiscoveryConfiguration discoveryConfig =
                mapper.readValue(resource.openStream(), ServiceDiscoveryConfiguration.class);

        assertFalse(discoveryConfig.isServiceEnabled("service1"));
        assertTrue(discoveryConfig.isServiceEnabled("service2"));
        assertFalse(discoveryConfig.isServiceEnabled("service-that-does-not-exist"));
    }

    @Test
    public void testBuilder() {
        BearerToken defaultApiToken = BearerToken.valueOf("someToken");
        SslConfiguration security = SslConfiguration.of(mock(Path.class));
        Duration defaultReadTimeout = Duration.seconds(30);
        Duration connectTimeout = Duration.hours(1);
        ProxyConfiguration defaultProxyConfiguration = ProxyConfiguration.of("globalsquid:3128");

        ServiceConfiguration service = ServiceConfiguration.builder()
                .security(security)
                .uris(ImmutableList.of("https://localhost:8443"))
                .connectTimeout(connectTimeout)
                .build();
        ServiceDiscoveryConfiguration services = ServiceDiscoveryConfiguration.builder()
                .defaultApiToken(defaultApiToken)
                .originalServices(ImmutableMap.of("service1", service))
                .defaultReadTimeout(defaultReadTimeout)
                .defaultProxyConfiguration(defaultProxyConfiguration)
                .build();

        assertEquals(defaultApiToken, services.defaultApiToken().get());
        assertEquals(defaultReadTimeout, services.defaultReadTimeout().get());
        assertEquals(defaultApiToken, services.getApiToken("service1").get());
        assertEquals(defaultApiToken, services.getServices().get("service1").apiToken().get());
        assertEquals(defaultReadTimeout, services.getReadTimeout("service1").get());
        assertEquals(defaultReadTimeout, services.getServices().get("service1").readTimeout().get());
        assertFalse(services.defaultSecurity().isPresent());
        assertEquals(security, services.getSecurity("service1").get());
        assertEquals(security, services.getServices().get("service1").security().get());
        assertEquals(connectTimeout, services.getServices().get("service1").connectTimeout().get());
        assertEquals(defaultProxyConfiguration, services.defaultProxyConfiguration().get());
        assertEquals(defaultProxyConfiguration, services.getServices().get("service1").proxyConfiguration().get());
    }
}
