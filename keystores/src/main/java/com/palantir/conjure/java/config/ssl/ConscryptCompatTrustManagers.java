/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Internal compatibility layer to work around
 * <a href="https://github.com/google/conscrypt/issues/1033">conscrypt#1033</a>.
 */
final class ConscryptCompatTrustManagers {

    static TrustManager[] wrap(TrustManager[] trustManager) {
        if (trustManager == null || trustManager.length == 0) {
            return trustManager;
        }
        TrustManager[] managers = new TrustManager[trustManager.length];
        for (int i = 0; i < managers.length; i++) {
            managers[i] = wrap(trustManager[i]);
        }
        return managers;
    }

    static TrustManager wrap(TrustManager trustManager) {
        if (trustManager.getClass().getName().contains("org.conscrypt")) {
            // We don't convert authType strings when a Conscrypt TrustManager is used.
            // This check should be equivalent to Conscrypt.isConscrypt without a dependecy
            // on Conscrypt itself.
            return trustManager;
        }
        if (trustManager instanceof ConscryptCompatX509TrustManager
                || trustManager instanceof ConscryptCompatX509ExtendedTrustManager) {
            // Already wrapped, nothing else is needed
            return trustManager;
        }
        if (trustManager instanceof X509ExtendedTrustManager) {
            return new ConscryptCompatX509ExtendedTrustManager((X509ExtendedTrustManager) trustManager);
        }
        if (trustManager instanceof X509TrustManager) {
            return new ConscryptCompatX509TrustManager((X509TrustManager) trustManager);
        }
        return trustManager;
    }

    private static final class ConscryptCompatX509TrustManager implements X509TrustManager {

        private final X509TrustManager delegate;

        ConscryptCompatX509TrustManager(X509TrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, conscryptToOpenjdkAuthType(authType));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, conscryptToOpenjdkAuthType(authType));
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            ConscryptCompatX509TrustManager that = (ConscryptCompatX509TrustManager) other;
            return delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public String toString() {
            return "ConscryptCompatX509TrustManager{" + delegate + '}';
        }
    }

    private static final class ConscryptCompatX509ExtendedTrustManager extends X509ExtendedTrustManager {

        private final X509ExtendedTrustManager delegate;

        ConscryptCompatX509ExtendedTrustManager(X509ExtendedTrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkServerTrusted(chain, conscryptToOpenjdkAuthType(authType), socket);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkServerTrusted(chain, conscryptToOpenjdkAuthType(authType), engine);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkServerTrusted(chain, conscryptToOpenjdkAuthType(authType));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            delegate.checkClientTrusted(chain, conscryptToOpenjdkAuthType(authType), socket);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            delegate.checkClientTrusted(chain, conscryptToOpenjdkAuthType(authType), engine);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            delegate.checkClientTrusted(chain, conscryptToOpenjdkAuthType(authType));
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return delegate.getAcceptedIssuers();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            ConscryptCompatX509ExtendedTrustManager that = (ConscryptCompatX509ExtendedTrustManager) other;
            return delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public String toString() {
            return "ConscryptCompatX509ExtendedTrustManager{" + delegate + '}';
        }
    }

    /**
     * See <a href="https://github.com/google/conscrypt/issues/1033">conscrypt#1033</a>.
     */
    private static String conscryptToOpenjdkAuthType(String authType) {
        if ("GENERIC".equals(authType)) {
            return "UNKNOWN";
        }
        return authType;
    }

    private ConscryptCompatTrustManagers() {}
}
