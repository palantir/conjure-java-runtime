/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.ssl;

import com.google.common.base.Throwables;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
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
        TrustManager[] trustManagers = createTrustManagerFactory(config.trust()).getTrustManagers();

        KeyManager[] keyManagers = null;
        if (config.key().isPresent()) {
            keyManagers = createKeyManagerFactory(config.key().get()).getKeyManagers();
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw Throwables.propagate(e);
        }
    }

    private static TrustManagerFactory createTrustManagerFactory(TrustStoreConfiguration trustStoreConfig) {
        try {
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(trustStoreConfig.type());
            try (InputStream stream = trustStoreConfig.uri().toURL().openStream()) {
                keyStore.load(stream, null);
            }
            trustManagerFactory.init(keyStore);

            return trustManagerFactory;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static KeyManagerFactory createKeyManagerFactory(KeyStoreConfiguration keyStoreConfig) {
        try {
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            KeyStore keyStore = KeyStore.getInstance(keyStoreConfig.type());
            try (InputStream stream = keyStoreConfig.uri().toURL().openStream()) {
                keyStore.load(stream, keyStoreConfig.password().toCharArray());
            }

            if (keyStoreConfig.alias().isPresent()) {
                // default KeyManagerFactory does not support referencing key by alias, so
                // if a key with a specific alias is desired, construct a new key store that
                // contains only the key and certificate with that alias
                keyStore = newKeyStoreWithEntry(keyStore, keyStoreConfig.password(), keyStoreConfig.alias().get());
            }

            keyManagerFactory.init(keyStore, keyStoreConfig.password().toCharArray());

            return keyManagerFactory;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Return a new {@link KeyStore} that contains the key and certificate chain with the provided alias in the given
     * key store.
     *
     * @param original
     *        key store that contains the key and certificate chain
     * @param password
     *        the password for the provided key store. Will also be used as the password of the returned key store.
     * @param alias
     *        the alias of the key and certificate chain in the provided key store
     * @return a newly constructed key store that contains a single entry that consists of the key and certificate chain
     *         with the provided alias in the given key store. The trust store will be of the same type as the provided
     *         one, will use the same password, and will store the key and certificate chain using the same alias.
     * @throws IllegalStateException
     *         if the provided key store does not contain a key and certificate chain with the given alias
     */
    private static KeyStore newKeyStoreWithEntry(KeyStore original, String password, String alias) {
        try {
            KeyStore newKeyStore = KeyStore.getInstance(original.getType());
            newKeyStore.load(null, password.toCharArray());

            Key aliasKey = original.getKey(alias, password.toCharArray());
            if (aliasKey == null) {
                throw new IllegalStateException(
                        String.format("Could not find key with alias \"%s\" in key store", alias));
            }
            Certificate[] certificateChain = original.getCertificateChain(alias);
            if (certificateChain == null) {
                throw new IllegalStateException(
                        String.format("Could not find certificate chain with alias \"%s\" in key store", alias));
            }

            newKeyStore.setKeyEntry(alias, aliasKey, password.toCharArray(), certificateChain);
            return newKeyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

}
