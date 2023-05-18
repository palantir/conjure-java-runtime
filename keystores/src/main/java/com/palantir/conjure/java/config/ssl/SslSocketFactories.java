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

package com.palantir.conjure.java.config.ssl;

import com.google.common.base.Throwables;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
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
     * Create an {@link SSLSocketFactory} from the provided configuration.
     *
     * @param config an {@link SslConfiguration} describing the trust store and key store configuration
     * @param provider The preferred security {@link Provider}
     * @return an {@link SSLSocketFactory} according to the input configuration
     */
    public static SSLSocketFactory createSslSocketFactory(SslConfiguration config, Provider provider) {
        SSLContext sslContext = createSslContext(config, provider);
        return sslContext.getSocketFactory();
    }

    /**
     * Create a {@link SSLSocketFactory} from the provided certificates.
     *
     * @param trustCertificatesByAlias a map of X.509 certificate in PEM or DER format by the alias to load the
     *     certificate as.
     */
    public static SSLSocketFactory createSslSocketFactory(Map<String, PemX509Certificate> trustCertificatesByAlias) {
        SSLContext sslContext = createSslContext(trustCertificatesByAlias);
        return sslContext.getSocketFactory();
    }

    /**
     * Create a {@link SSLSocketFactory} from the provided certificates.
     *
     * @param trustCertificatesByAlias a map of X.509 certificate in PEM or DER format by the alias to load the
     *     certificate as.
     * @param provider The preferred security {@link Provider}
     */
    public static SSLSocketFactory createSslSocketFactory(
            Map<String, PemX509Certificate> trustCertificatesByAlias, Provider provider) {
        SSLContext sslContext = createSslContext(trustCertificatesByAlias, provider);
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
        KeyManager[] keyManagers = createKeyManagers(config);

        return createSslContext(trustManagers, keyManagers);
    }

    /**
     * Create an {@link SSLContext} initialized from the provided configuration.
     *
     * @param config an {@link SslConfiguration} describing the trust store and key store configuration
     * @param provider The preferred security {@link Provider}
     * @return an {@link SSLContext} according to the input configuration
     */
    public static SSLContext createSslContext(SslConfiguration config, Provider provider) {
        TrustManager[] trustManagers = createTrustManagers(config);
        KeyManager[] keyManagers = createKeyManagers(config);

        return createSslContext(trustManagers, keyManagers, provider);
    }

    /**
     * Create an {@link SSLContext} initialized from the provided certificates.
     *
     * @param trustCertificatesByAlias a map of X.509 certificate in PEM or DER format by the alias to load the
     *     certificate as.
     */
    public static SSLContext createSslContext(Map<String, PemX509Certificate> trustCertificatesByAlias) {
        TrustManager[] trustManagers = createTrustManagers(trustCertificatesByAlias);
        return createSslContext(trustManagers, new KeyManager[] {});
    }

    /**
     * Create an {@link SSLContext} initialized from the provided certificates.
     *
     * @param trustCertificatesByAlias a map of X.509 certificate in PEM or DER format by the alias to load the
     *     certificate as.
     * @param provider The preferred security {@link Provider}
     */
    public static SSLContext createSslContext(
            Map<String, PemX509Certificate> trustCertificatesByAlias, Provider provider) {
        TrustManager[] trustManagers = createTrustManagers(trustCertificatesByAlias);
        return createSslContext(trustManagers, new KeyManager[] {}, provider);
    }

    /**
     * Create an {@link SSLContext} initialized from the provided certificates.
     * @see SSLContext#init(KeyManager[], TrustManager[], SecureRandom)
     */
    public static SSLContext createSslContext(TrustManager[] trustManagers, @Nullable KeyManager[] keyManagers) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Create an {@link SSLContext} initialized from the provided certificates using the supplied {@link Provider}.
     * @see SSLContext#init(KeyManager[], TrustManager[], SecureRandom)
     */
    public static SSLContext createSslContext(
            TrustManager[] trustManagers, @Nullable KeyManager[] keyManagers, Provider provider) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS", provider);
            sslContext.init(keyManagers, ConscryptCompatTrustManagers.wrap(trustManagers), null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Create a {@link TrustManager} array initialized from the provided configuration.
     *
     * @param config an {@link SslConfiguration} describing at least the trust store configuration
     * @return an {@link TrustManager} array according to the input configuration
     */
    public static TrustManager[] createTrustManagers(SslConfiguration config) {
        return ConscryptCompatTrustManagers.wrap(
                createTrustManagerFactory(config.trustStorePath(), config.trustStoreType())
                        .getTrustManagers());
    }

    /**
     * Create a {@link TrustManager} array initialized from the given certificates in PEM or DER format.
     *
     * @param trustCertificatesByAlias a map of X.509 certificate in PEM or DER format by the alias to load the
     *     certificate as.
     */
    public static TrustManager[] createTrustManagers(Map<String, PemX509Certificate> trustCertificatesByAlias) {
        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(KeyStores.createTrustStoreFromCertificates(trustCertificatesByAlias));
            return ConscryptCompatTrustManagers.wrap(trustManagerFactory.getTrustManagers());
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Create SSL socket factory and trust manager from the given configuration, see {@link #createX509TrustManager} and
     * {@link #createSslSocketFactory}.
     */
    public static TrustContext createTrustContext(SslConfiguration config) {
        TrustManager[] trustManagers = createTrustManagers(config);
        KeyManager[] keyManagers = createKeyManagers(config);
        SSLContext context = createSslContext(trustManagers, keyManagers);
        return TrustContext.of(context.getSocketFactory(), getX509TrustManager(trustManagers));
    }

    /**
     * Create SSL socket factory and trust manager from the given certificates, see {@link #createX509TrustManager} and
     * {@link #createSslSocketFactory}.
     */
    public static TrustContext createTrustContext(Map<String, PemX509Certificate> trustCertificatesByAlias) {
        TrustManager[] trustManagers = createTrustManagers(trustCertificatesByAlias);
        SSLContext context = createSslContext(trustManagers, new KeyManager[] {});
        return TrustContext.of(context.getSocketFactory(), getX509TrustManager(trustManagers));
    }

    /**
     * Returns the first {@link TrustManager} initialized from the given configuration. This is always an
     * {@link javax.net.ssl.X509TrustManager}.
     */
    public static X509TrustManager createX509TrustManager(SslConfiguration config) {
        return getX509TrustManager(createTrustManagers(config));
    }

    public static X509TrustManager createX509TrustManager(Map<String, PemX509Certificate> certificatesByAlias) {
        return getX509TrustManager(createTrustManagers(certificatesByAlias));
    }

    /**
     * Returns the first {@link TrustManager} from a loaded {@link TrustManagerFactory}. This is always an
     * {@link javax.net.ssl.X509TrustManager}.
     * @param trustManagers must be the result of {@link TrustManagerFactory#getTrustManagers()}
     * @return The first {@link TrustManager} which must be a {@link X509TrustManager}
     */
    public static X509TrustManager getX509TrustManager(TrustManager[] trustManagers) {
        TrustManager trustManager = trustManagers[0];
        if (trustManager instanceof X509TrustManager) {
            return (X509TrustManager) trustManager;
        } else {
            throw new SafeRuntimeException(
                    "First TrustManager associated with SslConfiguration was expected to be an X509TrustManager",
                    SafeArg.of("actualType", trustManager.getClass().getSimpleName()));
        }
    }

    /**
     * Create {@link KeyManager} array from the provided configuration.
     *
     * @param config an {@link SslConfiguration} describing at least the trust store configuration
     * @return an {@link KeyManager} array according to the input configuration
     */
    public static KeyManager[] createKeyManagers(SslConfiguration config) {
        return config.keyStorePath()
                .map(keyStorePath -> createKeyManagerFactory(
                                keyStorePath,
                                config.keyStorePassword(),
                                config.keyStoreType(),
                                config.keyStoreKeyAlias())
                        .getKeyManagers())
                .orElse(null);
    }

    private static TrustManagerFactory createTrustManagerFactory(
            Path trustStorePath, SslConfiguration.StoreType trustStoreType) {
        KeyStore keyStore;
        switch (trustStoreType) {
            case JKS:
            case PKCS12:
                keyStore = KeyStores.loadKeyStore(trustStoreType.name(), trustStorePath, Optional.empty());
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

        // Add globally trusted root CAs
        DefaultCas.getCertificates().forEach((certAlias, cert) -> {
            try {
                keyStore.setCertificateEntry(certAlias, cert);
            } catch (KeyStoreException e) {
                throw new SafeRuntimeException(
                        "Unable to add certificate to store", e, SafeArg.of("certificateAlias", certAlias));
            }
        });

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
            Optional<String> keyStorePassword,
            SslConfiguration.StoreType keyStoreType,
            Optional<String> keyStoreKeyAlias) {
        KeyStore keyStore;
        switch (keyStoreType) {
            case JKS:
            case PKCS12:
                keyStore = KeyStores.loadKeyStore(keyStoreType.name(), keyStorePath, keyStorePassword);
                break;
            case PEM:
                keyStore = KeyStores.createKeyStoreFromCombinedPems(keyStorePath);
                break;
            case PUPPET:
                Path puppetKeysDir = keyStorePath.resolve("private_keys");
                Path puppetCertsDir = keyStorePath.resolve("certs");
                keyStore = KeyStores.createKeyStoreFromPemDirectories(puppetKeysDir, ".pem", puppetCertsDir, ".pem");
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
            KeyManagerFactory keyManagerFactory =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(
                    keyStore, keyStorePassword.map(String::toCharArray).orElse(null));

            return keyManagerFactory;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }
}
