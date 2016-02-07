/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.trust;

import com.google.common.base.Optional;
import com.palantir.ssl.SslSocketFactories;
import java.nio.file.Path;
import javax.net.ssl.SSLSocketFactory;

/**
 * Utility functions for working with Java trust stores, and in particular for creating {@link SSLSocketFactory}s.
 *
 * @deprecated use {@link SslSocketFactories} instead.
 */
@Deprecated
public final class TrustStores {

    private TrustStores() {}

    /**
     * Create an {@link SSLSocketFactory} from the provided configuration.
     *
     * @param config a {@link TrustStoreConfiguration} describing the location, type and password of the desired trust
     *               store.
     * @return an {@link SSLSocketFactory} according to the input configuration
     */
    public static SSLSocketFactory createSslSocketFactory(TrustStoreConfiguration config) {
        return createSslSocketFactory(config.trustStorePath(), config.trustStoreType(), config.trustStorePassword());
    }

    /**
     * Create an {@link SSLSocketFactory} using the provided configuration.
     *
     * @param trustStorePath  location of the trust store
     * @param trustStoreType  optional type of the trust store, defualts to {@code JKS} when not provided
     * @param trustStorePassword optional password for the trust store (generally not required)
     * @return an {@link SSLSocketFactory} according to the input configuration
     */
    public static SSLSocketFactory createSslSocketFactory(
            Path trustStorePath,
            Optional<String> trustStoreType,
            Optional<String> trustStorePassword) {
        return SslSocketFactories.createSslSocketFactory(trustStorePath, trustStoreType, trustStorePassword);
    }
}
