/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

final class DefaultCas {

    private static final SafeLogger log = SafeLoggerFactory.get(DefaultCas.class);

    /**
     * This is managed by an excavator.
     */
    private static final String CA_CERTIFICATES_CRT = "ca-certificates.crt";

    private static final Supplier<Map<String, X509Certificate>> TRUSTED_CERTIFICATES =
            Suppliers.memoize(DefaultCas::getTrustedCertificates);

    static Map<String, X509Certificate> getCertificates() {
        return TRUSTED_CERTIFICATES.get();
    }

    private static Map<String, X509Certificate> getTrustedCertificates() {
        ImmutableMap.Builder<String, X509Certificate> certificateMap = ImmutableMap.builder();
        try {
            List<X509Certificate> caCertificates = KeyStores.readX509Certificates(
                            new ByteArrayInputStream(Resources.toByteArray(Resources.getResource(CA_CERTIFICATES_CRT))))
                    .stream()
                    .map(cert -> (X509Certificate) cert)
                    .collect(Collectors.toList());
            int index = 0;
            for (X509Certificate cert : caCertificates) {
                String certificateCommonName =
                        cert.getSubjectX500Principal().getName().toLowerCase(Locale.ENGLISH);
                certificateMap.put(certificateCommonName + index, cert);
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
        } catch (CertificateException | IOException e) {
            throw new SafeRuntimeException("Could not read file as an X.509 certificate", e);
        }

        return certificateMap.buildOrThrow();
    }

    private DefaultCas() {}
}
