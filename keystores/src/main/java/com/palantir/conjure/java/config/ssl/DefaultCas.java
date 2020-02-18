/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.config.ssl;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultCas {

    private static final Logger log = LoggerFactory.getLogger(DefaultCas.class);

    /**
     * This should be updated by running `./gradlew regenerateCAs` whenever the Java version we use to compile changes,
     * to ensure we pick up new CAs or revoke insecure ones.
     */
    private static final String CA_CERTIFICATES_CRT = "/ca-certificates.crt";

    private static final Supplier<Map<String, X509Certificate>> TRUSTED_CERTIFICATES =
            Suppliers.memoize(DefaultCas::getTrustedCertificates);

    static Map<String, X509Certificate> getCertificates() {
        return TRUSTED_CERTIFICATES.get();
    }

    private static Map<String, X509Certificate> getTrustedCertificates() {
        ImmutableMap.Builder<String, X509Certificate> certificateMap = ImmutableMap.builder();
        try {
            List<X509Certificate> caCertificates =
                    KeyStores.readX509Certificates(DefaultCas.class.getResourceAsStream(CA_CERTIFICATES_CRT)).stream()
                            .map(cert -> (X509Certificate) cert)
                            .collect(Collectors.toList());
            int index = 0;
            for (X509Certificate cert : caCertificates) {
                String certificateCommonName =
                        cert.getSubjectX500Principal().getName().toLowerCase(Locale.ENGLISH);
                certificateMap.put(certificateCommonName, cert);
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
        }

        return certificateMap.build();
    }

    private DefaultCas() {}
}
