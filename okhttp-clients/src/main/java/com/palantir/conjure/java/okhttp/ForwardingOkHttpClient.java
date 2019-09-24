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

package com.palantir.conjure.java.okhttp;

import java.net.Proxy;
import java.net.ProxySelector;
import java.util.List;
import javax.net.SocketFactory;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Authenticator;
import okhttp3.Cache;
import okhttp3.Call;
import okhttp3.CertificatePinner;
import okhttp3.ConnectionPool;
import okhttp3.ConnectionSpec;
import okhttp3.CookieJar;
import okhttp3.Dispatcher;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/** A forwarding/delegating {@link okhttp3.OkHttpClient}. Sub-classes should override individual methods. */
class ForwardingOkHttpClient extends OkHttpClient {

    private final OkHttpClient delegate;

    ForwardingOkHttpClient(OkHttpClient delegate) {
        this.delegate = delegate;
    }

    protected OkHttpClient getDelegate() {
        return delegate;
    }

    @Override
    public int connectTimeoutMillis() {
        return delegate.connectTimeoutMillis();
    }

    @Override
    public int readTimeoutMillis() {
        return delegate.readTimeoutMillis();
    }

    @Override
    public int writeTimeoutMillis() {
        return delegate.writeTimeoutMillis();
    }

    @Override
    public int pingIntervalMillis() {
        return delegate.pingIntervalMillis();
    }

    @Override
    public Proxy proxy() {
        return delegate.proxy();
    }

    @Override
    public ProxySelector proxySelector() {
        return delegate.proxySelector();
    }

    @Override
    public CookieJar cookieJar() {
        return delegate.cookieJar();
    }

    @Override
    public Cache cache() {
        return delegate.cache();
    }

    @Override
    public Dns dns() {
        return delegate.dns();
    }

    @Override
    public SocketFactory socketFactory() {
        return delegate.socketFactory();
    }

    @Override
    public SSLSocketFactory sslSocketFactory() {
        return delegate.sslSocketFactory();
    }

    @Override
    public HostnameVerifier hostnameVerifier() {
        return delegate.hostnameVerifier();
    }

    @Override
    public CertificatePinner certificatePinner() {
        return delegate.certificatePinner();
    }

    @Override
    public Authenticator authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Authenticator proxyAuthenticator() {
        return delegate.proxyAuthenticator();
    }

    @Override
    public ConnectionPool connectionPool() {
        return delegate.connectionPool();
    }

    @Override
    public boolean followSslRedirects() {
        return delegate.followSslRedirects();
    }

    @Override
    public boolean followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public boolean retryOnConnectionFailure() {
        return delegate.retryOnConnectionFailure();
    }

    @Override
    public Dispatcher dispatcher() {
        return delegate.dispatcher();
    }

    @Override
    public List<Protocol> protocols() {
        return delegate.protocols();
    }

    @Override
    public List<ConnectionSpec> connectionSpecs() {
        return delegate.connectionSpecs();
    }

    @Override
    public List<Interceptor> interceptors() {
        return delegate.interceptors();
    }

    @Override
    public List<Interceptor> networkInterceptors() {
        return delegate.networkInterceptors();
    }

    @Override
    public Call newCall(Request request) {
        return delegate.newCall(request);
    }

    @Override
    public WebSocket newWebSocket(Request request, WebSocketListener listener) {
        return delegate.newWebSocket(request, listener);
    }

    @Override
    public Builder newBuilder() {
        return delegate.newBuilder();
    }
}
