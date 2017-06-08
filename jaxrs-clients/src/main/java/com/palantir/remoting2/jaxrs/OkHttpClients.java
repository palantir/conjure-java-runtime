/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting2.jaxrs;

import com.google.common.collect.ImmutableList;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting2.clients.CipherSuites;
import com.palantir.remoting2.clients.ClientConfig;
import com.palantir.remoting2.config.service.BasicCredentials;
import com.palantir.remoting2.config.service.ProxyConfiguration;
import com.palantir.remoting2.config.service.ServiceConfiguration;
import com.palantir.remoting2.config.ssl.TrustContext;
import com.palantir.remoting2.tracing.okhttp3.OkhttpTraceInterceptor;
import java.util.concurrent.TimeUnit;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.Credentials;
import okhttp3.OkHttpClient;
import okhttp3.TlsVersion;

public class OkHttpClients {
    private OkHttpClients() {}

    public static OkHttpClient createClient(ServiceConfiguration serviceConfig) {
        ClientConfig config = ClientConfig.fromServiceConfig(serviceConfig);
        return createClient(config);
    }

    public static OkHttpClient createClient(ClientConfig config) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        // SSL
        if (config.trustContext().isPresent()) {
            TrustContext context = config.trustContext().get();
            client.sslSocketFactory(context.sslSocketFactory(), context.x509TrustManager());
        }

        // tracing
        client.interceptors().add(OkhttpTraceInterceptor.INSTANCE);

        // timeouts
        // Note that Feign overrides OkHttp timeouts with the timeouts given in FeignBuilder#Options if given, or
        // with its own default otherwise. Feign does not provide a mechanism for write timeouts. We thus need to set
        // write timeouts here and connect&read timeouts on FeignBuilder.
        client.writeTimeout(config.writeTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);
        client.connectTimeout(config.connectTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);
        client.readTimeout(config.readTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);

        // Set up HTTP proxy configuration
        if (config.proxy().isPresent()) {
            ProxyConfiguration proxy = config.proxy().get();
            client.proxy(proxy.toProxy());

            if (proxy.credentials().isPresent()) {
                BasicCredentials basicCreds = proxy.credentials().get();
                final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
                client.proxyAuthenticator((route, response) -> response.request().newBuilder()
                        .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                        .build());
            }
        }

        // cipher setup
        client.connectionSpecs(createConnectionSpecs(config.enableGcmCipherSuites()));

        // increase default connection pool from 5 @ 5 minutes to 100 @ 10 minutes
        client.connectionPool(new ConnectionPool(100, 10, TimeUnit.MINUTES));

        return client.build();
    }

    private static ImmutableList<ConnectionSpec> createConnectionSpecs(boolean enableGcmCipherSuites) {
        return ImmutableList.of(
            new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_2)
                    .cipherSuites(enableGcmCipherSuites
                            ? CipherSuites.allCipherSuites()
                            : CipherSuites.fastCipherSuites())
                    .build(),
            ConnectionSpec.CLEARTEXT);
    }

}
