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

package com.palantir.remoting.config.service;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.io.Resources;
import io.dropwizard.jackson.Jackson;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import org.junit.Test;

public final class ProxyConfigurationTests {
    private final ObjectMapper mapper = Jackson.newObjectMapper(new YAMLFactory());

    @Test
    public void testDeserializationWithoutCredentials() throws IOException {
        URL resource = Resources.getResource("configs/proxy-config-without-credentials.yml");
        ProxyConfiguration actualProxyConfiguration = mapper.readValue(resource.openStream(), ProxyConfiguration.class);

        ProxyConfiguration expectedProxyConfiguration = ProxyConfiguration.of("squid:3128");
        assertEquals(expectedProxyConfiguration, actualProxyConfiguration);
    }

    @Test
    public void testDeserializationWithCredentials() throws IOException {
        URL resource = Resources.getResource("configs/proxy-config-with-credentials.yml");
        ProxyConfiguration actualProxyConfiguration = mapper.readValue(resource.openStream(), ProxyConfiguration.class);

        ProxyConfiguration expectedProxyConfiguration =
                ProxyConfiguration.of("squid:3128", BasicCredentials.of("username", "password"));

        assertEquals(expectedProxyConfiguration, actualProxyConfiguration);
    }

    @Test
    public void testToProxy() {
        ProxyConfiguration proxyConfiguration = ProxyConfiguration.of("squid:3128");

        Proxy expected = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("squid", 3128));
        assertEquals(expected, proxyConfiguration.toProxy());
    }
}
