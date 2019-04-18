/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.config;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.net.HostAndPort;
import com.palantir.conjure.java.api.config.service.BasicCredentials;
import com.palantir.conjure.java.api.config.service.ServiceConfiguration;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.immutables.value.Value;

/**
 * A context-independent (i.e., does not depend on configuration files or on-disk entities like JKS keystores)
 * instantiation of a {@link ServiceConfiguration}.
 */
@Value.Immutable
@ImmutablesStyle
public interface ClientConfiguration {

    /** See {@link com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#security}. */
    SSLSocketFactory sslSocketFactory();

    /** See {@link com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#security}. */
    X509TrustManager trustManager();

    /** See {@link com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#uris}. */
    List<String> uris();

    /** See {@link com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#connectTimeout}. */
    Duration connectTimeout();

    /** See {@link com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#readTimeout}. */
    Duration readTimeout();

    /** See {@link com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#writeTimeout}. */
    Duration writeTimeout();

    /** See {@link com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#enableGcmCipherSuites}. */
    boolean enableGcmCipherSuites();

    /**
     * See {@link
     * com.palantir.conjure.java.api.config.service.PartialServiceConfiguration#fallbackToCommonNameVerification}.
     */
    boolean fallbackToCommonNameVerification();

    /** The proxy to use for the HTTP connection. */
    ProxySelector proxy();

    /** The credentials to use for the proxy selected by {@link #proxy}. */
    Optional<BasicCredentials> proxyCredentials();

    /**
     * Clients configured with a mesh proxy send all HTTP requests to the configured proxy address instead of the
     * configured {@link #uris uri}; requests carry an additional {@code Host} header (or http/2 {@code :authority}
     * pseudo-header) set to the configured {@link #uris uri}. The proxy is expected to forward such requests to the
     * original {@code #uris uri}.
     * <p>
     * Note that if this option is set, then the {@link #maxNumRetries} must also be set to 0 and exactly one {@link
     * #uris} must exist since the mesh proxy is expected to handle all retry logic.
     */
    Optional<HostAndPort> meshProxy();

    /** The maximum number of times a failed request is retried. */
    int maxNumRetries();

    /**
     * Indicates how the target node is selected for a given request.
     */
    NodeSelectionStrategy nodeSelectionStrategy();

    /**
     * The amount of time a URL marked as failed should be avoided for subsequent calls. If the
     * {@link #nodeSelectionStrategy} is ROUND_ROBIN, this must be a positive period of time.
     */
    Duration failedUrlCooldown();

    /**
     * The size of one backoff time slot for call retries. For example, an exponential backoff retry algorithm may
     * choose a backoff time in {@code [0, backoffSlotSize * 2^c]} for the c-th retry.
     */
    Duration backoffSlotSize();


    /** Indicates whether client-side sympathetic QoS should be enabled. */
    ClientQoS clientQoS();

    /** Indicates whether QosExceptions (other than RetryOther) should be propagated. */
    PropagateQoS propagateQoS();

    @Value.Check
    default void check() {
        if (meshProxy().isPresent()) {
            checkArgument(maxNumRetries() == 0, "If meshProxy is configured then maxNumRetries must be 0");
            checkArgument(uris().size() == 1, "If meshProxy is configured then uris must contain exactly 1 URI");
        }
        if (nodeSelectionStrategy().equals(NodeSelectionStrategy.ROUND_ROBIN)) {
            checkArgument(!failedUrlCooldown().isNegative() && !failedUrlCooldown().isZero(),
                    "If nodeSelectionStrategy is ROUND_ROBIN then failedUrlCooldown must be positive");
        }
    }

    static Builder builder() {
        return new Builder();
    }

    class Builder extends ImmutableClientConfiguration.Builder {}

    enum ClientQoS {
        /** Default. */
        ENABLED,

        /**
         * Disables the client-side sympathetic QoS. Consumers should almost never use this option, reserving it
         * for where there are known issues with the QoS interaction. Please consult project maintainers if applying
         * this option.
         */
        DANGEROUS_DISABLE_SYMPATHETIC_CLIENT_QOS
    }

    enum PropagateQoS {
        /** Default. */
        DISABLED,
        /**
         * Propagate QosException.Throttle and QosException.Unavailable (429/503) to the caller. Consumers should use
         * this when an upstream service has better context on how to handle the QoS error. This delegates the
         * responsibility to the upstream service, which should use an appropriate conjure client to handle the
         * response.
         *
         * For example, let us imagine a proxy server that serves both interactive and long-running background requests
         * by dispatching requests to some backend. Interactive requests should be retried relatively few times in
         * comparison to background jobs which run for minutes our even hours. The proxy server should use a backend
         * client that propagates the QoS responses instead of retrying so the proxy client can handle them
         * appropriately. There is no risk of retry storms because the retries are isolated to one layer, the proxy
         * client.
         *
         * Note that QosException.RetryOther (308) is not propagated. If the proxy server is exposed on the front door
         * but the backend is not, it makes no sense to redirect the caller to a new backend. The client will still
         * follow redirects.
         */
        ENABLED
    }
}
