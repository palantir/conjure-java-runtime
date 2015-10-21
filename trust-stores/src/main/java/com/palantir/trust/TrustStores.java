/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.trust;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

/**
 * Utility functions for working with Java trust stores, and in particular for creating {@link SSLSocketFactory}s.
 */
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
        return createSslSocketFactory(
                config.trustStorePath(),
                config.trustStoreType(),
                config.trustStorePassword());
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
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(trustStoreType.or("JKS"));
            keyStore.load(Files.newInputStream(trustStorePath), trustStorePassword.transform(TO_CHAR_ARRAY).orNull());

            trustManagerFactory.init(keyStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext.getSocketFactory();
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
