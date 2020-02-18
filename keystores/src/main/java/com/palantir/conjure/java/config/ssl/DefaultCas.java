/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.config.ssl;

import com.google.common.base.Suppliers;
import com.google.common.hash.Hashing;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultCas {

    private static final Logger log = LoggerFactory.getLogger(DefaultCas.class);

    /**
     * This should be updated by running `./gradlew regenerateCAs` whenever the Java version we use to compile changes,
     * to ensure we pick up new CAs or revoke insecure ones.
     */
    private static final String CA_CERTIFICATES_CRT = "/ca-certificates.crt";

    private static final Supplier<X509TrustManager> TRUST_MANAGER = Suppliers.memoize(DefaultCas::buildTrustManager);

    static X509TrustManager getTrustManager() {
        return TRUST_MANAGER.get();
    }

    private static X509TrustManager buildTrustManager() {
        try {
            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(getTrustedCertificates());
            return (X509TrustManager) trustManagerFactory.getTrustManagers()[0];
        } catch (KeyStoreException | NoSuchAlgorithmException e) {
            throw new SafeRuntimeException("Unable to create X509TrustManager from default ", e);
        }
    }

    private static KeyStore getTrustedCertificates() {
        KeyStore keyStore = createKeyStore();
        try {
            List<X509Certificate> caCertificates =
                    getCertificatesFromFile(DefaultCas.class.getResourceAsStream(CA_CERTIFICATES_CRT));
            int index = 0;
            for (X509Certificate cert : caCertificates) {
                String certificateCommonName =
                        cert.getSubjectX500Principal().getName().toLowerCase(Locale.ENGLISH);
                keyStore.setCertificateEntry(certificateCommonName, cert);
                log.debug(
                        "Adding CA certificate",
                        SafeArg.of("certificateIndex", index),
                        SafeArg.of("certificateCount", caCertificates.size()),
                        SafeArg.of("certificateCommonName", certificateCommonName),
                        SafeArg.of("expiry", cert.getNotAfter()),
                        SafeArg.of(
                                "sha256Fingerprint",
                                Hashing.sha256().hashBytes(cert.getEncoded()).toString()));
                index++;
            }
        } catch (CertificateException e) {
            throw new SafeRuntimeException("Could not read file as an X.509 certificate", e);
        } catch (KeyStoreException e) {
            throw new SafeRuntimeException("Could not add certificate to trust store", e);
        }

        return keyStore;
    }

    /**
     * Returns a List of certificates representing the PEM formatted X.509 or PKCS#7 certificates in the provided file.
     * The order of the certificates in the list will match the order that the certificates occur in the file.
     *
     * @param certFile file that contains PEM formatted X.509 or PKCS#7 certificates. The string can contain other
     *     content as well (for example, RSA keys or other information) as long as properly formatted certificate
     *     content exists in the string.
     * @return list of Certificates that represent the X.509 certificates that were found in the input file in the order
     *     that they appeared in the file. Will be empty if no certificates were found in the input file.
     */
    private static List<X509Certificate> getCertificatesFromFile(InputStream certFile) throws CertificateException {
        return CertificateFactory.getInstance("X.509").generateCertificates(certFile).stream()
                .map(cert -> (X509Certificate) cert)
                .collect(Collectors.toList());
    }

    private static KeyStore createKeyStore() {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("pkcs12");
            keyStore.load(null, null);
        } catch (GeneralSecurityException | IOException e) {
            throw new SafeRuntimeException(e);
        }
        return keyStore;
    }

    private DefaultCas() {}
}
