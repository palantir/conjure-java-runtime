/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.http;

import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.okhttp.BraveOkHttpRequestResponseInterceptor;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.net.HttpHeaders;
import com.google.common.net.InetAddresses;
import com.palantir.config.service.BasicCredentials;
import com.palantir.config.service.ServiceConfiguration;
import com.palantir.config.service.ServiceDiscoveryConfiguration;
import com.palantir.config.service.proxy.EmptyProxyConfigurationProvider;
import com.palantir.config.service.proxy.ProxyConfiguration;
import com.palantir.config.service.proxy.ProxyConfigurationProvider;
import com.palantir.config.service.proxy.WrapperProxyConfigurationProvider;
import com.palantir.ext.brave.SlfLoggingSpanCollector;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.Logger.Level;
import feign.Request;
import feign.Target;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import io.dropwizard.util.Duration;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import javax.net.ssl.SSLSocketFactory;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Response;
import okhttp3.Route;

/**
 * Factory for initializing Feign-based HTTP-invoking dynamic proxies around service interfaces.
 */
public final class FeignClientFactory {

    private static final Duration CONNECT_TIMEOUT = Duration.minutes(10);
    private static final Duration READ_TIMEOUT = Duration.minutes(10);

    private final Contract contract;
    private final Encoder encoder;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final ClientSupplier clientSupplier;
    private final BackoffStrategy backoffStrategy;
    private final Request.Options options;
    private final String userAgent;
    private final ProxyConfigurationProvider proxyConfigurationProvider;

    private FeignClientFactory(
            Contract contract,
            Encoder encoder,
            Decoder decoder,
            ErrorDecoder errorDecoder,
            ClientSupplier clientSupplier,
            BackoffStrategy backoffStrategy,
            Request.Options options,
            String userAgent,
            ProxyConfigurationProvider proxyConfigurationProvider) {
        this.contract = contract;
        this.encoder = encoder;
        this.decoder = decoder;
        this.errorDecoder = errorDecoder;
        this.clientSupplier = clientSupplier;
        this.backoffStrategy = backoffStrategy;
        this.options = options;
        this.userAgent = userAgent;
        this.proxyConfigurationProvider = proxyConfigurationProvider;
    }

    /**
     * Returns a new instance using the specified contract/encoder/decoder/client when constructing clients, using a
     * {@link NeverRetryingBackoffStrategy} to attempt connecting via each of the alternative target URLs at most once
     * per proxy call.
     */
    public static FeignClientFactory of(
            Contract contract,
            Encoder encoder,
            Decoder decoder,
            ErrorDecoder errorDecoder,
            ClientSupplier clientSupplier,
            String userAgent) {
        return new FeignClientFactory(contract, encoder, decoder, errorDecoder, clientSupplier,
                NeverRetryingBackoffStrategy.INSTANCE, new Request.Options(), userAgent,
                new EmptyProxyConfigurationProvider());
    }

    /**
     * Returns a new instance using the specified contract/encoder/decoder/client when constructing clients.
     */
    public static FeignClientFactory of(
            Contract contract,
            Encoder encoder,
            Decoder decoder,
            ErrorDecoder errorDecoder,
            ClientSupplier clientSupplier,
            BackoffStrategy backoffStrategy,
            Request.Options options,
            String userAgent) {
        return new FeignClientFactory(
                contract, encoder, decoder, errorDecoder, clientSupplier, backoffStrategy, options, userAgent,
                new EmptyProxyConfigurationProvider());
    }

    /**
     * Returns a new instance using the specified contract/encoder/decoder/client when constructing clients.
     */
    public static FeignClientFactory of(
            Contract contract,
            Encoder encoder,
            Decoder decoder,
            ErrorDecoder errorDecoder,
            ClientSupplier clientSupplier,
            BackoffStrategy backoffStrategy,
            Request.Options options,
            String userAgent,
            ProxyConfigurationProvider proxyConfigurationProvider) {
        return new FeignClientFactory(
                contract, encoder, decoder, errorDecoder, clientSupplier, backoffStrategy, options, userAgent,
                proxyConfigurationProvider);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and {@link
     * okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, String uri, Class<T> type) {
        return createProxy(sslSocketFactory, uri, type, proxyConfigurationProvider);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and {@link
     * okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, String uri, Class<T> type,
            Request.Options requestOptions) {
        return createProxy(sslSocketFactory, uri, type, proxyConfigurationProvider, requestOptions);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and {@link
     * okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, String uri, Class<T> type,
            ProxyConfigurationProvider proxyConfigurationProvider) {
        return createProxy(sslSocketFactory, uri, type, proxyConfigurationProvider, options);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and {@link
     * okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, String uri, Class<T> type,
            ProxyConfigurationProvider proxyConfigurationProvider, Request.Options requestOptions) {
        return createProxy(sslSocketFactory, ImmutableSet.of(uri), type, proxyConfigurationProvider, requestOptions);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and
     * {@link okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}. A {@link FailoverFeignTarget}
     * is used to cycle through the given uris on failure if there's more than one uri.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, Set<String> uris, Class<T> type) {
        return createProxy(sslSocketFactory, uris, type, options);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and {@link
     * okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}. A {@link FailoverFeignTarget} is used
     * to cycle through the given uris on failure if there's more than one uri.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, Set<String> uris, Class<T> type,
            Request.Options requestOptions) {
        return createProxy(sslSocketFactory, uris, type, proxyConfigurationProvider, requestOptions);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, proxy
     * configuration provider, and {@link okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}.
     * A {@link FailoverFeignTarget} is used to cycle through the given uris on failure if there's more than one uri.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, Set<String> uris, Class<T> type,
                             ProxyConfigurationProvider proxyConfigurationProvider) {
        return createProxy(sslSocketFactory, uris, type, proxyConfigurationProvider, options);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, the proxy
     * if present and {@link okhttp3.OkHttpClient} wrapped in {@link feign.okhttp.OkHttpClient}. A
     * {@link FailoverFeignTarget} is used to cycle through the given uris on failure if there's more than one uri.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, Set<String> uris, Class<T> type,
                             ProxyConfigurationProvider proxyConfigurationProvider, Request.Options requestOptions) {

        ClientConfiguration configuration = new ImmutableClientConfiguration.Builder()
                .sslSocketFactory(sslSocketFactory)
                .proxyConfigurationProvider(proxyConfigurationProvider)
                .userAgent(userAgent)
                .build();

        Client client = clientSupplier.createClient(configuration);
        Feign.Builder builder = Feign.builder()
                .contract(contract)
                .encoder(encoder)
                .decoder(decoder)
                .errorDecoder(errorDecoder)
                .options(requestOptions)
                .logger(new Slf4jLogger(FeignClients.class))
                .logLevel(Level.BASIC)
                .requestInterceptor(UserAgentInterceptor.of(userAgent));

        Target<T> target;
        if (uris.size() > 1) {
            FailoverFeignTarget<T> retryTarget = new FailoverFeignTarget<>(uris, type, backoffStrategy);
            builder.retryer(retryTarget);
            client = retryTarget.wrapClient(client);
            target = retryTarget;
        } else {
            target = new Target.HardCodedTarget<>(type, Iterables.getOnlyElement(uris));
        }

        return builder.client(client)
                .target(target);
    }

    /**
     * Constructs a dynamic proxy for the specified type using the {@link ServiceConfiguration} obtained from the
     * supplied {@link ServiceDiscoveryConfiguration} for the specified service name.
     */
    public <T> T createProxy(
            ServiceDiscoveryConfiguration discoveryConfig, String serviceName, Class<T> serviceClass) {

        ServiceConfiguration serviceConfig = Preconditions.checkNotNull(
                discoveryConfig.getServices().get(serviceName),
                "Unable to find the configuration for " + serviceName + ".");

        Optional<SSLSocketFactory> socketFactory = Optional.absent();
        Optional<ProxyConfiguration> proxyConfiguration = discoveryConfig.getProxyConfiguration(serviceName);
        Optional<SslConfiguration> sslConfig = discoveryConfig.getSecurity(serviceName);

        if (sslConfig.isPresent()) {
            socketFactory = Optional.of(SslSocketFactories.createSslSocketFactory(sslConfig.get()));
        }

        Set<String> uris = Sets.newHashSet(serviceConfig.uris());
        Request.Options requestOptions = new Request.Options((int) serviceConfig.connectTimeout()
                .or(CONNECT_TIMEOUT).toMilliseconds(), (int) serviceConfig.readTimeout()
                .or(READ_TIMEOUT).toMilliseconds());
        return createProxy(socketFactory, uris, serviceClass, new WrapperProxyConfigurationProvider(proxyConfiguration),
                requestOptions);
    }

    /**
     * Constructs a list, corresponding to the iteration order of the supplied endpoints, of dynamic proxies for the
     * specified type, using the supplied SSL factory if it is present.
     */
    public <T> List<T> createProxies(
            Optional<SSLSocketFactory> sslSocketFactory, Collection<String> endpointUris, Class<T> type) {
        List<T> ret = Lists.newArrayListWithCapacity(endpointUris.size());
        for (String uri : endpointUris) {
            ret.add(createProxy(sslSocketFactory, uri, type));
        }
        return ret;
    }

    private static final ClientSupplier OKHTTP_CLIENT_SUPPLIER =
            new ClientSupplier() {
                @Override
                public Client createClient(ClientConfiguration clientConfiguration) {
                    okhttp3.OkHttpClient.Builder client = new okhttp3.OkHttpClient.Builder();

                    Optional<SSLSocketFactory> sslSocketFactory = clientConfiguration.sslSocketFactory();
                    if (sslSocketFactory.isPresent()) {
                        client.sslSocketFactory(sslSocketFactory.get());
                    }

                    Optional<ProxyConfiguration> proxyConfiguration = clientConfiguration.proxyConfigurationProvider()
                            .getProxyConfiguration();
                    if (proxyConfiguration.isPresent()) {
                        ProxyConfiguration proxy = proxyConfiguration.get();
                        client.proxy(proxy.toProxy());

                        if (proxy.credentials().isPresent()) {
                            BasicCredentials basicCreds = proxy.credentials().get();
                            final String credentials = Credentials.basic(basicCreds.username(), basicCreds.password());
                            client.proxyAuthenticator(new Authenticator() {
                                @Override
                                public okhttp3.Request authenticate(Route route, Response response) throws IOException {
                                    return response.request().newBuilder()
                                            .header(HttpHeaders.PROXY_AUTHORIZATION, credentials)
                                            .build();
                                }
                            });
                        }
                    }

                    String userAgent = clientConfiguration.userAgent();
                    // Set up Zipkin/Brave tracing
                    ClientTracer tracer = ClientTracer.builder()
                            .traceSampler(Sampler.ALWAYS_SAMPLE)
                            .randomGenerator(new Random())
                            .state(new ThreadLocalServerClientAndLocalSpanState(
                                    getIpAddress(), 0 /** Client TCP port. */, userAgent))
                            .spanCollector(new SlfLoggingSpanCollector("ClientTracer(" + userAgent + ")"))
                            .build();
                    BraveOkHttpRequestResponseInterceptor braveInterceptor =
                            new BraveOkHttpRequestResponseInterceptor(
                                    new ClientRequestInterceptor(tracer),
                                    new ClientResponseInterceptor(tracer),
                                    new DefaultSpanNameProvider());
                    client.addInterceptor(braveInterceptor);

                    return new OkHttpClient(client.build());
                }
            };

    private static final ClientSupplier DEFAULT_CLIENT_SUPPLIER =
            new ClientSupplier() {
                @Override
                public Client createClient(ClientConfiguration clientConfiguration) {
                    return new Client.Default(clientConfiguration.sslSocketFactory().orNull(), null);
                }
            };

    /**
     * Supplies a feign {@link Client} wrapping a {@link okhttp3.OkHttpClient} client with optionally specified {@link
     * SSLSocketFactory}. The client emits Zipkin/Brave traces to a {@link java.util.logging.Logger} with log-level
     * INFO.
     */
    public static ClientSupplier okHttpClient() {
        return OKHTTP_CLIENT_SUPPLIER;
    }

    /**
     * Supplies a feign {@link feign.Client.Default} client with default {@link javax.net.ssl.HostnameVerifier} and
     * optionally specified {@link SSLSocketFactory}.
     * @deprecated will be removed in a future version, use {@link #okHttpClient} instead
     */
    @Deprecated
    public static ClientSupplier defaultClient() {
        return DEFAULT_CLIENT_SUPPLIER;
    }

    // Returns the IP address returned by InetAddress.getLocalHost(), or -1 if it cannot be determined.
    // TODO(rfink) What if there are multiple? Can we find the "correct" one?
    private static int getIpAddress() {
        try {
            return InetAddresses.coerceToInteger(InetAddress.getLocalHost());
        } catch (UnknownHostException e) {
            return -1;
        }
    }
}
