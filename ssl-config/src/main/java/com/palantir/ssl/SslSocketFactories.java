/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.ssl;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.palantir.ssl.SslConfiguration.Builder;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

/**
 * Utility functions for creating {@link SSLSocketFactory}s
 * that are configured with Java trust stores and key stores.
 */
public final class SslSocketFactories {
    private SslSocketFactories() {}

    /**
     * Create an {@link SSLSocketFactory} using the provided configuration.
     *
     * @param trustStorePath  location of the trust store
     * @param trustStoreType  optional type of the trust store, defaults to {@code JKS} when not provided
     * @param trustStorePassword optional password for the trust store (generally not required)
     * @return an {@link SSLSocketFactory} according to the input configuration
     */
    public static SSLSocketFactory createSslSocketFactory(
            Path trustStorePath,
            Optional<String> trustStoreType,
            Optional<String> trustStorePassword) {
        Builder builder = SslConfiguration.builder();
        builder.trustStorePath(trustStorePath);
        builder.trustStoreType(trustStoreType);
        builder.trustStorePassword(trustStorePassword);

        return createSslSocketFactory(builder.build());
    }

    /**
     * Create an {@link SSLSocketFactory} from the provided configuration.
     *
     * @param config a {@link SslConfiguration} describing the location, type and password of the desired trust
     *               store.
     * @return an {@link SSLSocketFactory} according to the input configuration
     */
    public static SSLSocketFactory createSslSocketFactory(SslConfiguration config) {
        SSLContext sslContext = createSslContext(config);
        return sslContext.getSocketFactory();
    }

    /**
     * Create an {@link SSLContext} initialized from the provided configuration.
     */
    @VisibleForTesting
    static SSLContext createSslContext(SslConfiguration config) {
        TrustManager[] trustManagers = createTrustManagerFactory(
                config.trustStorePath(),
                config.trustStoreType(),
                config.trustStorePassword()).getTrustManagers();

        KeyManager[] keyManagers = null;
        if (config.keyStorePath().isPresent()) {
            keyManagers = createKeyManagerFactory(
                    config.keyStorePath().get(),
                    config.keyStoreType(),
                    config.keyStorePassword()).getKeyManagers();
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    private static TrustManagerFactory createTrustManagerFactory(
            Path trustStorePath,
            Optional<String> trustStoreType,
            Optional<String> trustStorePassword) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(trustStoreType.or("JKS"));
            try (InputStream stream = Files.newInputStream(trustStorePath)) {
                keyStore.load(stream, trustStorePassword.transform(TO_CHAR_ARRAY).orNull());
            }
            trustManagerFactory.init(keyStore);

            return trustManagerFactory;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static KeyManagerFactory createKeyManagerFactory(
            Path keyStorePath,
            Optional<String> keyStoreType,
            Optional<String> keyStorePassword) {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(keyStoreType.or("JKS"));
            try (InputStream stream = Files.newInputStream(keyStorePath)) {
                keyStore.load(stream, keyStorePassword.transform(TO_CHAR_ARRAY).orNull());
            }
            keyManagerFactory.init(keyStore, keyStorePassword.transform(TO_CHAR_ARRAY).orNull());

            return keyManagerFactory;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static final Function<String, char[]> TO_CHAR_ARRAY = new Function<String, char[]>() {
        @Override
        public char[] apply(String input) {
            return input.toCharArray();
        }
    };

}
