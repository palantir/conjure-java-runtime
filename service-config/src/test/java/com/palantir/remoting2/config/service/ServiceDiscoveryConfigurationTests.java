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

package com.palantir.remoting2.config.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import com.palantir.remoting2.config.ssl.SslConfiguration;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import com.palantir.remoting2.ext.jackson.ShimJdk7Module;
import com.palantir.tokens2.auth.BearerToken;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Test;

/**
 * Tests for {@link ServiceDiscoveryConfiguration}.
 */
public final class ServiceDiscoveryConfigurationTests {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory())
            .registerModule(new ShimJdk7Module())
            .registerModule(new Jdk8Module());

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
        assertEquals(ImmutableList.of("https://some.internal.url:8443/thirdservice/api"),
                discoveryConfig.getUris("service3"));
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
    public void testMergingExplicitWithDefaultProperties() {
        BearerToken defaultApiToken = BearerToken.valueOf("someToken");
        SslConfiguration security = SslConfiguration.of(mock(Path.class));
        Duration defaultReadTimeout = Duration.seconds(30);
        Duration connectTimeout = Duration.hours(1);
        Duration defaultConnectTimeout = Duration.hours(2);
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
                .defaultConnectTimeout(defaultConnectTimeout)
                .build();

        // Test service discovery merging
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
        assertEquals(defaultConnectTimeout, services.defaultConnectTimeout().get());
        assertEquals(defaultProxyConfiguration, services.defaultProxyConfiguration().get());
        assertEquals(defaultProxyConfiguration, services.getServices().get("service1").proxyConfiguration().get());

        // Test specifying explicit service conf to merge with
        ServiceConfiguration serviceWithDefaults = services.getServiceWithDefaults(service);
        assertEquals(defaultApiToken, serviceWithDefaults.apiToken().get());
        assertEquals(defaultReadTimeout, serviceWithDefaults.readTimeout().get());
        assertEquals(defaultProxyConfiguration, serviceWithDefaults.proxyConfiguration().get());
        assertEquals(security, serviceWithDefaults.security().get());
        assertEquals(connectTimeout, serviceWithDefaults.connectTimeout().get());
    }

    @Test
    public void serDe() throws Exception {
        ServiceDiscoveryConfiguration serialized = ServiceDiscoveryConfiguration.builder()
                .defaultApiToken(BearerToken.valueOf("bearerToken"))
                .defaultSecurity(SslConfiguration.of(Paths.get("truststore.jks")))
                .putOriginalServices("service", ServiceConfiguration.of("uri", Optional.empty()))
                .defaultProxyConfiguration(ProxyConfiguration.of("host:80"))
                .defaultConnectTimeout(Duration.days(1))
                .defaultReadTimeout(Duration.days(1))
                .build();
        String deserializedCamelCase = "{\"apiToken\":\"bearerToken\",\"security\":"
                + "{\"trustStorePath\":\"truststore.jks\",\"trustStoreType\":\"JKS\",\"keyStorePath\":null,"
                + "\"keyStorePassword\":null,\"keyStoreType\":\"JKS\",\"keyStoreKeyAlias\":null},\"services\":"
                + "{\"service\":{\"apiToken\":null,\"security\":null,\"connectTimeout\":null,\"readTimeout\":null,"
                + "\"writeTimeout\":null,\"enableGcmCipherSuites\":null,\"uris\":[\"uri\"],"
                + "\"proxyConfiguration\":null}},\"proxyConfiguration\":"
                + "{\"hostAndPort\":\"host:80\",\"credentials\":null,\"type\":\"HTTP\"},\"connectTimeout\":\"1 day\","
                + "\"readTimeout\":\"1 day\",\"enableGcmCipherSuites\":null}";
        String deserializedKebabCase = "{\"api-token\":\"bearerToken\",\"security\":"
                + "{\"trust-store-path\":\"truststore.jks\",\"trust-store-type\":\"JKS\",\"key-store-path\":null,"
                + "\"key-store-password\":null,\"key-store-type\":\"JKS\",\"key-store-key-alias\":null},\"services\":"
                + "{\"service\":{\"apiToken\":null,\"security\":null,\"connect-timeout\":null,\"read-timeout\":null,"
                + "\"write-timeout\":null,\"uris\":[\"uri\"],\"enable-gcm-cipher-suites\":null,"
                + "\"proxy-configuration\":null}},\"proxy-configuration\":"
                + "{\"host-and-port\":\"host:80\",\"credentials\":null},\"connect-timeout\":\"1 day\","
                + "\"read-timeout\":\"1 day\"}";

        assertThat(ObjectMappers.newClientObjectMapper().writeValueAsString(serialized))
                .isEqualTo(deserializedCamelCase);
        assertThat(ObjectMappers.newClientObjectMapper()
                .readValue(deserializedCamelCase, ServiceDiscoveryConfiguration.class))
                .isEqualTo(serialized);
        assertThat(ObjectMappers.newClientObjectMapper()
                .readValue(deserializedKebabCase, ServiceDiscoveryConfiguration.class))
                .isEqualTo(serialized);
    }

    @Test
    public void serDe_optional() throws Exception {
        ServiceDiscoveryConfiguration serialized = ServiceDiscoveryConfiguration.builder().build();
        String deserializedCamelCase = "{\"apiToken\":null,\"security\":null,\"services\":{},"
                + "\"proxyConfiguration\":null,\"connectTimeout\":null,\"readTimeout\":null,"
                + "\"enableGcmCipherSuites\":null}";
        String deserializedKebabCase = "{\"api-token\":null,\"security\":null,\"services\":{},"
                + "\"proxy-configuration\":null,\"connect-timeout\":null,\"read-timeout\":null,"
                + "\"enable-gcm-cipher-suites\":null}";

        assertThat(ObjectMappers.newClientObjectMapper().writeValueAsString(serialized))
                .isEqualTo(deserializedCamelCase);
        assertThat(ObjectMappers.newClientObjectMapper()
                .readValue(deserializedCamelCase, ServiceDiscoveryConfiguration.class))
                .isEqualTo(serialized);
        assertThat(ObjectMappers.newClientObjectMapper()
                .readValue(deserializedKebabCase, ServiceDiscoveryConfiguration.class))
                .isEqualTo(serialized);
    }

}
