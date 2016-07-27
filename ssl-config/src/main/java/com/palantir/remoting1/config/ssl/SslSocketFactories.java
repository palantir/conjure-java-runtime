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

package com.palantir.remoting1.config.ssl;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Utility functions for creating {@link SSLSocketFactory}s that are configured with Java trust stores and key stores.
 */
public final class SslSocketFactories {
    private SslSocketFactories() {}

    /**
     * Create an {@link SSLSocketFactory} from the provided configuration.
     *
     * @param config an {@link SslConfiguration} describing the trust store and key store configuration
     * @return an {@link SSLSocketFactory} according to the input configuration
     */
    public static SSLSocketFactory createSslSocketFactory(SslConfiguration config) {
        SSLContext sslContext = createSslContext(config);
        return sslContext.getSocketFactory();
    }

    /**
     * Create an {@link SSLContext} initialized from the provided configuration.
     *
     * @param config an {@link SslConfiguration} describing the trust store and key store configuration
     * @return an {@link SSLContext} according to the input configuration
     */
    public static SSLContext createSslContext(SslConfiguration config) {
        TrustManager[] trustManagers = createTrustManagers(config);

        KeyManager[] keyManagers = null;
        if (config.keyStorePath().isPresent()) {
            keyManagers = createKeyManagerFactory(
                    config.keyStorePath().get(),
                    config.keyStorePassword().get(),
                    config.keyStoreType(),
                    config.keyStoreKeyAlias()).getKeyManagers();
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Create a {@link TrustManager[]} initialized from the provided configuration.
     *
     * @param config an {@link SslConfiguration} describing at least the trust store configuration
     * @return an {@link TrustManager[]} according to the input configuration
     */
    public static TrustManager[] createTrustManagers(SslConfiguration config) {
        return createTrustManagerFactory(config.trustStorePath(), config.trustStoreType()).getTrustManagers();
    }

    /**
     * Returns the first {@link TrustManager} initialized from the given configuration. This is always an {@link
     * javax.net.ssl.X509TrustManager}.
     */
    public static X509TrustManager createX509TrustManager(SslConfiguration config) {
        TrustManager trustManager = createTrustManagers(config)[0];
        if (trustManager instanceof X509TrustManager) {
            return (X509TrustManager) trustManager;
        } else {
            throw new RuntimeException(String.format(
                    "First TrustManager associated with SslConfiguration was expected to be a %s, but was a %s: %s",
                    X509TrustManager.class.getSimpleName(),
                    trustManager.getClass().getSimpleName(),
                    config.trustStorePath()));
        }
    }

    private static TrustManagerFactory createTrustManagerFactory(
            Path trustStorePath,
            SslConfiguration.StoreType trustStoreType) {

        KeyStore keyStore;
        switch (trustStoreType) {
            case JKS:
            case PKCS12:
                keyStore = KeyStores.loadKeyStore(trustStoreType.name(), trustStorePath, Optional.<String>absent());
                break;
            case PEM:
                keyStore = KeyStores.createTrustStoreFromCertificates(trustStorePath);
                break;
            case PUPPET:
                Path puppetCertsDir = trustStorePath.resolve("certs");
                if (!puppetCertsDir.toFile().isDirectory()) {
                    throw new IllegalStateException(
                            String.format("Puppet certs directory did not exist at path \"%s\"", puppetCertsDir));
                }
                keyStore = KeyStores.createTrustStoreFromCertificates(puppetCertsDir);
                break;
            default:
                throw new IllegalStateException("Unrecognized trust store type: " + trustStoreType);
        }

        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);

            return trustManagerFactory;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    private static KeyManagerFactory createKeyManagerFactory(
            Path keyStorePath,
            String keyStorePassword,
            SslConfiguration.StoreType keyStoreType,
            Optional<String> keyStoreKeyAlias) {

        KeyStore keyStore;
        switch (keyStoreType) {
            case JKS:
            case PKCS12:
                keyStore = KeyStores.loadKeyStore(keyStoreType.name(), keyStorePath, Optional.of(keyStorePassword));
                break;
            case PEM:
                keyStore = KeyStores.createKeyStoreFromCombinedPems(keyStorePath, keyStorePassword);
                break;
            case PUPPET:
                Path puppetKeysDir = keyStorePath.resolve("private_keys");
                Path puppetCertsDir = keyStorePath.resolve("certs");
                keyStore = KeyStores.createKeyStoreFromPemDirectories(
                        puppetKeysDir,
                        ".pem",
                        puppetCertsDir,
                        ".pem",
                        keyStorePassword);
                break;
            default:
                throw new IllegalStateException("Unrecognized key store type: " + keyStoreType);
        }

        if (keyStoreKeyAlias.isPresent()) {
            // default KeyManagerFactory does not support referencing key by alias, so
            // if a key with a specific alias is desired, construct a new key store that
            // contains only the key and certificate with that alias
            keyStore = KeyStores.newKeyStoreWithEntry(keyStore, keyStorePassword, keyStoreKeyAlias.get());
        }

        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

            return keyManagerFactory;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

}
