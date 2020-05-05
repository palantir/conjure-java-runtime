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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.base.Throwables;
import com.google.common.io.BaseEncoding;
import com.palantir.conjure.java.config.ssl.pkcs1.Pkcs1PrivateKeyReader;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateKeySpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class KeyStores {

    private static final Cache<EqualByteArray, X509Certificate> certCache =
            Caffeine.newBuilder().maximumSize(1024).softValues().build();

    /**
     * Pattern that matches a single RSA key in a PEM file. Has a capture group that captures the content of the key
     * (everything that occurs between the header and footer).
     */
    private static final Pattern KEY_PATTERN = Pattern.compile(
            "-----BEGIN (RSA)? ?PRIVATE KEY-----\n?(.+?)\n?-----END (RSA)? ?PRIVATE KEY-----", Pattern.DOTALL);

    /**
     * Pattern that matches a single certificate in a PEM file. Has a capture group that captures the content of the
     * certificate (everything that occurs between the header and footer).
     */
    private static final Pattern CERT_PATTERN =
            Pattern.compile("-----BEGIN CERTIFICATE-----\n?(.+?)\n?-----END CERTIFICATE-----", Pattern.DOTALL);

    private static final FileFilter VISIBLE_FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File pathname) {
            return !pathname.isHidden();
        }
    };

    private KeyStores() {}

    /**
     * Returns a {@link KeyStore} created by loading the certificate or certificates specified by the provided path.
     *
     * @param path a path to an X.509 certificate in PEM or DER format, or to a directory containing such files. If the
     *     path specifies a directory, every non-hidden file in the directory must be a file of the specified format or
     *     an exception is thrown.
     * @return a new KeyStore of type {@link KeyStore#getDefaultType()} that contains the certificates specified by the
     *     provided path. The name of the file used to create a certificate entry is used as the alias for the entry.
     *     The returned store will not have any password.
     */
    static KeyStore createTrustStoreFromCertificates(Path path) {
        KeyStore keyStore;
        keyStore = createKeyStore();

        for (File currFile : getFilesForPath(path)) {
            try (InputStream in = new BufferedInputStream(Files.newInputStream(currFile.toPath()))) {
                addCertificatesToKeystore(keyStore, currFile.getName(), readX509Certificates(in));
            } catch (IOException e) {
                throw new RuntimeException(
                        String.format("IOException encountered when opening '%s'", currFile.toPath()), e);
            } catch (CertificateException | KeyStoreException e) {
                throw new RuntimeException(
                        String.format("Could not read file at \"%s\" as an X.509 certificate", currFile.toPath()), e);
            }
        }

        return keyStore;
    }

    /**
     * Returns a {@link KeyStore} created by loading the given certificates.
     *
     * @param certificatesByAlias a map of X.509 certificate in PEM or DER format by the alias to load the certificate
     *     as.
     */
    static KeyStore createTrustStoreFromCertificates(Map<String, PemX509Certificate> certificatesByAlias) {
        KeyStore keyStore;
        keyStore = createKeyStore();

        for (Map.Entry<String, PemX509Certificate> entry : certificatesByAlias.entrySet()) {
            try (InputStream certIn =
                    new ByteArrayInputStream(entry.getValue().pemCertificate().getBytes(StandardCharsets.UTF_8))) {
                addCertificatesToKeystore(keyStore, entry.getKey(), readX509Certificates(certIn));
            } catch (IOException e) {
                throw Throwables.propagate(e);
            } catch (KeyStoreException | CertificateException e) {
                throw new RuntimeException(
                        String.format(
                                "Could not read certificate alias \"%s\" as an X.509 certificate", entry.getKey()),
                        e);
            }
        }

        return keyStore;
    }

    private static void addCertificatesToKeystore(
            KeyStore keyStore, String certificateEntryNamePrefix, List<Certificate> certificates)
            throws KeyStoreException {
        int certIndex = 0;
        for (Certificate cert : certificates) {
            keyStore.setCertificateEntry(certificateEntryNamePrefix + "-" + certIndex, cert);
            certIndex++;
        }
    }

    /**
     * Returns a {@link KeyStore} created by loading the combined PEM (a single PEM with both the key and certificate
     * chain) file or files specified by the provided path.
     *
     * @param filePathOrDirectory a path to a PEM file containing a PKCS#1 RSA private key and the certificate(s) for
     *     that key in PEM format, or to a directory containing such files. If the path specifies a directory, every
     *     non-hidden file in the directory must be a file of the specified format or an exception is thrown.
     * @return a new KeyStore of type {@link KeyStore#getDefaultType()} that contains the key entries specified by the
     *     provided path. The name of the file used to create a key entry is used as the alias for the entry. The
     *     provided password is used to secure the key store and all of the key entries.
     */
    static KeyStore createKeyStoreFromCombinedPems(Path filePathOrDirectory) {
        try {
            KeyStore keyStore = KeyStore.getInstance("pkcs12");
            keyStore.load(null, null);

            for (File currFile : getFilesForPath(filePathOrDirectory)) {
                KeyStore.PrivateKeyEntry privateKeyEntry = readKeyEntryFromPems(currFile.toPath(), currFile.toPath());
                keyStore.setKeyEntry(
                        currFile.getName(),
                        privateKeyEntry.getPrivateKey(),
                        null,
                        privateKeyEntry.getCertificateChain());
            }

            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Returns a {@link KeyStore} created by loading the PEM files for keys and certificates from the directories at the
     * specified paths. Every visible file in the keyDirPath directory that ends with keyExtension will be read in as a
     * private key with a certificate chain that comes from a file in the certDirPath directory that consists of the
     * base name of the file concatenated with certExtension. The key files must contain an RSA private key in PKCS#1
     * format and the certificate files must contain X.509 certificates. Throws an exception if either path that is
     * provided is not a directory or if there is any key which does not have a corresponding certificate.
     *
     * @param keyDirPath path to the directory that contains the key files (PKCS#1 in PEM format)
     * @param keyExtension file extension for the files in the keyDirPath directory that should be considered keys. Is
     *     used to perform a suffix match. Case-sensitive and should include a period character if it is desired (for
     *     example, ".key", ".pem").
     * @param certDirPath path to the directory that contains the certificate files for the keys
     * @param certExtension file extension for the files in certDirPath directory that should be considered certificate
     *     files. For every key file, the file "fileName - keyExtension + certExtension" must exist in the certDirPath.
     *     Case-sensitive and should include a period character if it is desired (for example, ".cer", ".pem").
     * @return a new KeyStore of type {@link KeyStore#getDefaultType()} that contains the key entries specified by the
     *     arguments. The base name of the file used to create a key entry is used as the alias for the entry. The
     *     provided password is used to secure the key store and all of the key entries.
     */
    static KeyStore createKeyStoreFromPemDirectories(
            Path keyDirPath, String keyExtension, Path certDirPath, String certExtension) {
        if (!keyDirPath.toFile().isDirectory()) {
            throw new IllegalStateException(String.format("keyDirPath is not a directory: \"%s\"", keyDirPath));
        } else if (!certDirPath.toFile().isDirectory()) {
            throw new IllegalStateException(String.format("certDirPath is not a directory: \"%s\"", certDirPath));
        }

        try {
            KeyStore keyStore = KeyStore.getInstance("pkcs12");
            keyStore.load(null, null);

            File[] keyFiles = getFilesForPath(keyDirPath);
            for (File currKeyFile : keyFiles) {
                // find key files based on extension
                String currKeyFileName = currKeyFile.getName();
                if (currKeyFileName.endsWith(keyExtension)) {
                    // derive cert file based on key file
                    String baseName = currKeyFileName.substring(0, currKeyFileName.length() - keyExtension.length());
                    Path currCertPath = certDirPath.resolve(baseName + certExtension);

                    KeyStore.PrivateKeyEntry privateKeyEntry = readKeyEntryFromPems(currKeyFile.toPath(), currCertPath);
                    keyStore.setKeyEntry(
                            baseName, privateKeyEntry.getPrivateKey(), null, privateKeyEntry.getCertificateChain());
                }
            }

            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new SafeRuntimeException("Failed to create key store from PEM directories", e);
        }
    }

    /**
     * Returns an array of files for the specified path. If the specified path is a directory, returns an array of the
     * visible (not hidden) files in the directory. Otherwise, returns an array with a single element that consists of
     * the file referred to by the provided path.
     */
    private static File[] getFilesForPath(Path path) {
        File[] files;
        File pathFile = path.toFile();

        if (pathFile.isDirectory()) {
            files = pathFile.listFiles(VISIBLE_FILE_FILTER);
            if (files == null) {
                throw new IllegalStateException(String.format("failed to list visible files in directory %s", path));
            }
        } else {
            files = new File[] {pathFile};
        }

        return files;
    }

    /**
     * Returns a {@link KeyStore} created by by loading all of the entries in the store specified by the provided
     * parameters.
     *
     * @param storeType the type of the store specified by the provided path. Must be a type accepted by
     *     {@link KeyStore#getInstance(String)}. The returned store will also be of this type.
     * @param path the path to the existing store to load.
     * @param password an optional password used to read the provided store. If absent, no password will be used to read
     *     the store.
     * @return a new KeyStore of type storeType that contains the entries read from the store at the provided path using
     *     the provided password.
     */
    static KeyStore loadKeyStore(String storeType, Path path, Optional<String> password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(storeType);
            try (InputStream stream = Files.newInputStream(path)) {
                char[] passwordChars = password.map(String::toCharArray).orElse(null);
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
     * @param original key store that contains the key and certificate chain
     * @param password the password for the provided key store. Will also be used as the password of the returned key
     *     store.
     * @param alias the alias of the key and certificate chain in the provided key store
     * @return a newly constructed key store that contains a single entry that consists of the key and certificate chain
     *     with the provided alias in the given key store. The trust store will be of the same type as the provided one,
     *     will use the same password, and will store the key and certificate chain using the same alias.
     * @throws IllegalStateException if the provided key store does not contain a key and certificate chain with the
     *     given alias
     */
    static KeyStore newKeyStoreWithEntry(KeyStore original, Optional<String> password, String alias) {
        try {
            KeyStore newKeyStore = KeyStore.getInstance(original.getType());
            char[] passwordChar = password.map(String::toCharArray).orElse(null);
            newKeyStore.load(null, passwordChar);

            Key aliasKey = original.getKey(alias, passwordChar);
            if (aliasKey == null) {
                throw new IllegalStateException(
                        String.format("Could not find key with alias \"%s\" in key store", alias));
            }
            Certificate[] certificateChain = original.getCertificateChain(alias);
            if (certificateChain == null) {
                throw new IllegalStateException(
                        String.format("Could not find certificate chain with alias \"%s\" in key store", alias));
            }

            newKeyStore.setKeyEntry(alias, aliasKey, passwordChar, certificateChain);
            return newKeyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
    }

    private static KeyStore createKeyStore() {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
        } catch (GeneralSecurityException | IOException e) {
            throw Throwables.propagate(e);
        }
        return keyStore;
    }

    static List<Certificate> readX509Certificates(InputStream certificateIn) throws CertificateException {
        return CertificateFactory.getInstance("X.509").generateCertificates(certificateIn).stream()
                .map(cert -> getCertFromCache((X509Certificate) cert))
                .collect(Collectors.toList());
    }

    /**
     * Returns a KeyStore.PrivateKeyEntry consisting of the private key and certificate chain in the files at the
     * provided paths. The key file must contain an RSA private key in PEM format (PKCS#1) and the certificate file must
     * contain the certificate chain for the key in PEM format. The key and cert file paths may be the same if there is
     * a single file that contains both the key and the certificates.
     *
     * @param privateKeyFilePath path to a file that contains the RSA private key in PEM format (PKCS#1)
     * @param certFilePath path to a file that contains the certificates for the key in PEM format. If there are
     *     multiple certificates in the chain, the file should contain them in chaining order.
     * @return a KeyStore.PrivateKeyEntry that consists of the private key and the certificate chain for the key read
     *     from the provided paths.
     */
    private static KeyStore.PrivateKeyEntry readKeyEntryFromPems(Path privateKeyFilePath, Path certFilePath) {
        // read private key
        String keyPemFileString;
        PrivateKey privateKey;
        try {
            keyPemFileString = readFileAsString(privateKeyFilePath);
            privateKey = getPrivateKeyFromString(keyPemFileString);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(
                    String.format("Failed to read private key from file at \"%s\"", privateKeyFilePath), e);
        }

        // read certificates
        Certificate[] certificates;
        try {
            // if key and cert file are the same, use string that was already created
            String certPemFileString =
                    privateKeyFilePath.equals(certFilePath) ? keyPemFileString : readFileAsString(certFilePath);
            certificates = getCertificatesFromString(certPemFileString).toArray(new Certificate[0]);
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(
                    String.format("Failed to read certificates from file at \"%s\"", certFilePath), e);
        }

        return new KeyStore.PrivateKeyEntry(privateKey, certificates);
    }

    private static String readFileAsString(Path path) throws IOException {
        byte[] fileBytes = Files.readAllBytes(path);
        return new String(fileBytes, StandardCharsets.UTF_8);
    }

    /**
     * Returns a PrivateKey representing the first PEM formatted RSA key in the provided string. Throws an exception if
     * the provided string does not contain a valid RSA key.
     *
     * @param pemFileString string that contains PEM formatted RSA key (PKCS#1). The string can contain other content as
     *     well (for example, certificates or other information) as long as properly formatted RSA key content exists in
     *     the string.
     * @return PrivateKey representing the first RSA key in the provided string
     */
    static PrivateKey getPrivateKeyFromString(String pemFileString) throws GeneralSecurityException {
        Matcher matcher = KEY_PATTERN.matcher(pemFileString);
        if (!matcher.find() || !Objects.equals(matcher.group(1), matcher.group(3))) {
            throw new GeneralSecurityException(
                    String.format("unable to find valid RSA key in the provided string: %s", pemFileString));
        }

        // get content between headers and strip newlines to get Base64 encoded ASN1 DER only
        String privateKeyString = matcher.group(2).replace("\n", "");

        // read private key
        byte[] privateKeyDerBytes = BaseEncoding.base64().decode(privateKeyString);

        KeySpec rsaPrivKeySpec = "RSA".equals(matcher.group(1))
                ? parsePkcs1PrivateKey(privateKeyDerBytes)
                : parsePkcs8PrivateKey(privateKeyDerBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(rsaPrivKeySpec);
    }

    static RSAPrivateKeySpec parsePkcs1PrivateKey(byte[] pkcs1DerBytes) {
        return new Pkcs1PrivateKeyReader(pkcs1DerBytes).readRsaKey();
    }

    static PKCS8EncodedKeySpec parsePkcs8PrivateKey(byte[] pkcs8DerBytes) {
        return new PKCS8EncodedKeySpec(pkcs8DerBytes);
    }

    /**
     * Returns a List of certificates representing the PEM formatted X.509 certificates in the provided string. The
     * order of the certificates in the list will match the order that the certificates occur in the string.
     *
     * @param pemFileString string that contains PEM formatted X.509 certificates. The string can contain other content
     *     as well (for example, RSA keys or other information) as long as properly formatted certificate content exists
     *     in the string.
     * @return list of Certificates that represent the X.509 certificates that were found in the input string in the
     *     order that they appeared in the string. Will be empty if no certificates were found in the input string.
     */
    private static List<Certificate> getCertificatesFromString(String pemFileString)
            throws IOException, CertificateException {
        Matcher matcher = CERT_PATTERN.matcher(pemFileString);

        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        List<Certificate> certList = new ArrayList<>();
        while (matcher.find()) {
            try (InputStream stream = new ByteArrayInputStream(matcher.group().getBytes(StandardCharsets.UTF_8))) {
                certList.add(getCertFromCache((X509Certificate) certFactory.generateCertificate(stream)));
            }
        }

        return certList;
    }

    private static X509Certificate getCertFromCache(X509Certificate certificate) {
        try {
            return certCache.get(new EqualByteArray(certificate.getEncoded()), input -> certificate);
        } catch (CertificateEncodingException e) {
            throw new SafeRuntimeException("Unable to get certificate bytes", e);
        }
    }

    private static class EqualByteArray {

        private final byte[] bytes;
        private int hash;

        EqualByteArray(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public int hashCode() {
            if (hash == 0 && bytes.length > 0) {
                hash = Arrays.hashCode(bytes);
            }
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof EqualByteArray)) {
                return false;
            }
            EqualByteArray other = (EqualByteArray) obj;
            return Arrays.equals(this.bytes, other.bytes);
        }
    }
}
