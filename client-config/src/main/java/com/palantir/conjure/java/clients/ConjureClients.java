/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.clients;

import com.google.errorprone.annotations.CheckReturnValue;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import com.palantir.conjure.java.api.config.service.ServicesConfigBlock;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.HostEventsSink;
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;
import java.time.Duration;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

/** These interfaces are for implementing clientfactories. They are designed to be immutable. */
public final class ConjureClients {
    private ConjureClients() {}

    public interface ReloadingClientFactory {
        /**
         * Construct an instance of the {@code clientInterface} parameter which can be used to make network calls to
         * the service identified by {@code serviceName} in the given {@link ServicesConfigBlock}.
         *
         * This client is expected to live-reload when the given {@link Refreshable} changes, so changes to URIs or
         * other config should happen transparently.
         *
         * Implementations of this interface may or may not cache client instances internally.
         */
        <T> T get(Class<T> clientInterface, String serviceName);
    }

    public interface NonReloadingClientFactory {
        /**
         * Construct an instance of the given {@code clientInterface} which can be used to make network calls to the
         * single conceptual upstream identified by {@code serviceConf}.
         *
         * Behaviour is undefined if {@code serviceConf} contains no URIs.
         */
        <T> T getNonReloading(Class<T> clientInterface, ServiceConfiguration serviceConf);
    }

    /**
     * Options which users may want to tune when building a client.
     *
     * Implementors of this interface are expected to return their own type as the type parameter {@link T}, so that
     * calls to these methods can be chained. Intended to be immutable.
     */
    @CheckReturnValue
    public interface WithClientOptions<T> {

        /** How should a client choose which URI to send requests to. */
        T withNodeSelectionStrategy(NodeSelectionStrategy strategy);

        /**
         * The amount of time a URL marked as failed should be avoided for subsequent calls. Implementations may
         * ignore this value.
         */
        T withFailedUrlCooldown(Duration duration);

        T withClientQoS(ClientConfiguration.ClientQoS value);

        T withServerQoS(ClientConfiguration.ServerQoS value);

        T withRetryOnTimeout(ClientConfiguration.RetryOnTimeout value);

        T withMaxNumRetries(int maxNumRetries);

        T withTaggedMetrics(TaggedMetricRegistry metrics);

        T withUserAgent(UserAgent agent);

        /** Per-host success/failure information will be recorded to this sink. */
        T withHostEventsSink(HostEventsSink hostEventsSink);

        /** The SSL configuration needed to interact with the service. */
        T withSslSocketFactory(SSLSocketFactory sslSocketFactory);

        /** The SSL configuration needed to interact with the service. */
        T withTrustManager(X509TrustManager trustManager);

        T withSecurityProvider(java.security.Provider securityProvider);
    }

    public interface ToReloadingFactory<U> {
        @CheckReturnValue
        U reloading(Refreshable<ServicesConfigBlock> scb);
    }
}
