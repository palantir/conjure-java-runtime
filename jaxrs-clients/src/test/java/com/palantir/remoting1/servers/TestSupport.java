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

package com.palantir.remoting1.servers;

import com.github.kristofa.brave.Brave;
import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.ssl.SslConfiguration;
import com.palantir.remoting1.config.ssl.SslSocketFactories;
import com.palantir.remoting1.jaxrs.JaxRsClient;
import com.palantir.remoting1.jaxrs.TestEchoService;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class TestSupport {
    private TestSupport() {}

    static TestEchoService createProxy(int port, String name, Brave brave) {
        String endpointUri = "https://localhost:" + port;
        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        return JaxRsClient.builder(
                ClientConfig.builder()
                        .trustContext(SslSocketFactories.createTrustContext(sslConfig))
                        .build())
                .withTracer(brave)
                .build(TestEchoService.class, name, endpointUri);
    }

    static ch.qos.logback.classic.Logger getLogger(String name) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(name);
        logger.setLevel(ch.qos.logback.classic.Level.ALL);
        return logger;
    }

    static ch.qos.logback.classic.Logger getLogger(Class<?> clazz) {
        return getLogger(clazz.getName());
    }

    static void logDebugBrave(String tracerName, Logger logger, Brave brave) {
        logger.debug("'{}' test [{}]: traceId: '{}', tracer: {}",
                tracerName, Thread.currentThread().getName(), MDC.get("traceId"), brave);
        logger.debug("  Server: {}", brave.serverSpanThreadBinder().getCurrentServerSpan().getSpan());
        logger.debug("  Client: {}", brave.clientSpanThreadBinder().getCurrentClientSpan());
        logger.debug("  Local:  {}", brave.localSpanThreadBinder().getCurrentLocalSpan());
    }
}
