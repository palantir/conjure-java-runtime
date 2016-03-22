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

package com.palantir.remoting.ssl;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

final class KeyStores {

    private KeyStores() {
    }

    /**
     * Returns a {@link KeyStore} created by loading the certificate or certificates specified by the provided path.
     *
     * @param path
     *        a path to a X.509 certificate in PEM or DER format, or to a directory containing certificate files. If the
     *        path specifies a directory, every non-hidden file in the directory must be a X.509 certificate in PEM or
     *        DER format.
     * @return a new KeyStore of type {@link KeyStore#getDefaultType()} that contains the certificates specified by the
     *         provided path. The returned store will not have any password.
     */
    static KeyStore createTrustStoreFromCertificates(Path path) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);

            File pathFile = path.toFile();
            if (pathFile.isDirectory()) {
                File[] files = pathFile.listFiles(VISIBLE_FILE_FILTER);
                if (files != null) {
                    for (File currFile : files) {
                        keyStore.setCertificateEntry(currFile.getName(), readX509Certificate(currFile.toPath()));
                    }
                }
            } else {
                keyStore.setCertificateEntry(pathFile.getName(), readX509Certificate(pathFile.toPath()));
            }

            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Returns a {@link KeyStore} created by by loading all of the entries in the store specified by the provided
     * parameters.
     *
     * @param storeType
     *        the type of the store specified by the provided path. Must be a type accepted by
     *        {@link KeyStore#getInstance(String)}. The returned store will also be of this type.
     * @param path
     *        the path to the existing store to load.
     * @param password
     *        an optional password used to read the provided store. If absent, no password will be used to read the
     *        store.
     * @return a new KeyStore of type storeType that contains the entries read from the store at the provided path using
     *         the provided password.
     */
    static KeyStore loadKeyStore(String storeType, Path path, Optional<String> password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(storeType);
            try (InputStream stream = Files.newInputStream(path)) {
                char[] passwordChars = password.isPresent() ? password.get().toCharArray() : null;
                keyStore.load(stream, passwordChars);
            }
            return keyStore;
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
    static KeyStore newKeyStoreWithEntry(KeyStore original, String password, String alias) {
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

    private static Certificate readX509Certificate(Path certificateFilePath) {
        try {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            Certificate certificate;
            try (InputStream stream = Files.newInputStream(certificateFilePath)) {
                certificate = certFactory.generateCertificate(stream);
            }
            return certificate;
        } catch (GeneralSecurityException | IOException e) {
            throw new RuntimeException(String.format("Could not read file at \"%s\" as an X.509 certificate",
                    certificateFilePath.toAbsolutePath()), e);
        }
    }

    private static final FileFilter VISIBLE_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return !pathname.isHidden();
        }
    };

}
