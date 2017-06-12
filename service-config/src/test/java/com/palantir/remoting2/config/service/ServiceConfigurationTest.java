/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import com.palantir.remoting2.config.ssl.SslConfiguration;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import com.palantir.tokens2.auth.BearerToken;
import java.nio.file.Paths;
import org.junit.Test;

public final class ServiceConfigurationTest {

    @Test
    public void serDe() throws Exception {
        ServiceConfiguration serialized = ServiceConfiguration.builder()
                .apiToken(BearerToken.valueOf("bearerToken"))
                .security(SslConfiguration.of(Paths.get("truststore.jks")))
                .connectTimeout(Duration.days(1))
                .readTimeout(Duration.days(1))
                .writeTimeout(Duration.days(1))
                .addUris("uri1")
                .proxyConfiguration(ProxyConfiguration.of("host:80"))
                .build();
        String deserializedCamelCase = "{\"apiToken\":\"bearerToken\",\"security\":"
                + "{\"trustStorePath\":\"truststore.jks\",\"trustStoreType\":\"JKS\",\"keyStorePath\":null,"
                + "\"keyStorePassword\":null,\"keyStoreType\":\"JKS\",\"keyStoreKeyAlias\":null},"
                + "\"connectTimeout\":\"1 day\",\"readTimeout\":\"1 day\",\"writeTimeout\":\"1 day\","
                + "\"enableGcmCipherSuites\":null,"
                + "\"uris\":[\"uri1\"],\"proxyConfiguration\":{\"hostAndPort\":\"host:80\",\"credentials\":null,"
                + "\"type\":\"HTTP\"}}";
        String deserializedKebabCase = "{\"api-token\":\"bearerToken\",\"security\":"
                + "{\"trust-store-path\":\"truststore.jks\",\"trust-store-type\":\"JKS\",\"key-store-path\":null,"
                + "\"key-store-password\":null,\"key-store-type\":\"JKS\",\"key-store-key-alias\":null},"
                + "\"connect-timeout\":\"1 day\",\"read-timeout\":\"1 day\",\"write-timeout\":\"1 day\","
                + "\"uris\":[\"uri1\"],\"proxy-configuration\":{\"host-and-port\":\"host:80\",\"credentials\":null},"
                + "\"enable-gcm-cipher-suites\":null}";

        assertThat(ObjectMappers.newClientObjectMapper().writeValueAsString(serialized))
                .isEqualTo(deserializedCamelCase);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedCamelCase, ServiceConfiguration.class))
                .isEqualTo(serialized);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedKebabCase, ServiceConfiguration.class))
                .isEqualTo(serialized);
    }

    @Test
    public void serDe_optional() throws Exception {
        ServiceConfiguration serialized = ServiceConfiguration.builder().build();
        String deserializedCamelCase = "{\"apiToken\":null,\"security\":null,\"connectTimeout\":null,"
                + "\"readTimeout\":null,\"writeTimeout\":null,\"enableGcmCipherSuites\":null,"
                + "\"uris\":[],\"proxyConfiguration\":null}";
        String deserializedKebabCase = "{\"api-token\":null,\"security\":null,\"connect-timeout\":null,"
                + "\"read-timeout\":null,\"write-timeout\":null,\"enable-gcm-cipher-suites\":null,"
                + "\"uris\":[],\"proxy-configuration\":null}";

        assertThat(ObjectMappers.newClientObjectMapper().writeValueAsString(serialized))
                .isEqualTo(deserializedCamelCase);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedCamelCase, ServiceConfiguration.class))
                .isEqualTo(serialized);
        assertThat(ObjectMappers.newClientObjectMapper().readValue(deserializedKebabCase, ServiceConfiguration.class))
                .isEqualTo(serialized);
    }

}
