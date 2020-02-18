/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.config.ssl;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.X509TrustManager;

public final class CompositeX509TrustManager implements X509TrustManager {
    private final List<X509TrustManager> trustManagers;

    public CompositeX509TrustManager(List<X509TrustManager> trustManagers) {
        this.trustManagers = trustManagers;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        firstNonNullResult(trustManager -> trustManager.checkClientTrusted(chain, authType));
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        firstNonNullResult(trustManager -> trustManager.checkServerTrusted(chain, authType));
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return trustManagers.stream()
                .map(X509TrustManager::getAcceptedIssuers)
                .flatMap(Arrays::stream)
                .toArray(X509Certificate[]::new);
    }

    private void firstNonNullResult(CertificateExceptionConsumer resultFunction) throws CertificateException {
        Optional<CertificateException> certificateException = Optional.empty();

        for (X509TrustManager trustManager : trustManagers) {
            try {
                resultFunction.checkCertificate(trustManager);
                return;
            } catch (CertificateException e) {
                certificateException = Optional.of(e);
            }
        }

        if (certificateException.isPresent()) {
            throw certificateException.get();
        }
    }

    @FunctionalInterface
    interface CertificateExceptionConsumer {
        void checkCertificate(X509TrustManager trustManager) throws CertificateException;
    }
}
