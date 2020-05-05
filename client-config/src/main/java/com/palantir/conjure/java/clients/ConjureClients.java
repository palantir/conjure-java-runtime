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
import com.palantir.conjure.java.client.config.NodeSelectionStrategy;
import com.palantir.refreshable.Refreshable;
import com.palantir.tritium.metrics.registry.TaggedMetricRegistry;

/** These interfaces are for implementing clientfactories. They are designed to be immutable. */
public final class ConjureClients {
    private ConjureClients() {}

    public interface ReloadingClientFactory {
        <T> T get(Class<T> serviceClass, String serviceName);
    }

    public interface NonReloadingClientFactory {
        <T> T getNonReloading(Class<T> clazz, ServiceConfiguration serviceConf);
    }

    @CheckReturnValue
    public interface WithClientBehaviour<T> {

        T withTaggedMetrics(TaggedMetricRegistry metrics);

        T withUserAgent(UserAgent agent);

        T withNodeSelectionStrategy(NodeSelectionStrategy strategy);

        T withClientQoS(ClientConfiguration.ClientQoS value);

        T withServerQoS(ClientConfiguration.ServerQoS value);

        T withRetryOnTimeout(ClientConfiguration.RetryOnTimeout value);

        T withSecurityProvider(java.security.Provider securityProvider);

        T withMaxNumRetries(int maxNumRetries);
    }

    public interface ToReloadingFactory<U> {
        @CheckReturnValue
        U reloading(Refreshable<ServicesConfigBlock> scb);
    }

    public interface Factory
            extends NonReloadingClientFactory, WithClientBehaviour<Factory>, ToReloadingFactory<ReloadingFactory> {}

    public interface ReloadingFactory
            extends ReloadingClientFactory,
                    NonReloadingClientFactory,
                    WithClientBehaviour<ReloadingFactory>,
                    ToReloadingFactory<ReloadingFactory> {}
}
