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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.palantir.config.service.ServiceConfiguration;
import com.palantir.config.service.ServiceDiscoveryConfiguration;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.Logger.Level;
import feign.Request;
import feign.TraceResponseDecoder;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import io.dropwizard.util.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLSocketFactory;

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
    private final Function<Optional<SSLSocketFactory>, Client> clientSupplier;
    private final BackoffStrategy backoffStrategy;
    private final Request.Options options;
    private final UserAgentInterceptor userAgentInterceptor;

    private FeignClientFactory(
            Contract contract,
            Encoder encoder,
            Decoder decoder,
            ErrorDecoder errorDecoder,
            Function<Optional<SSLSocketFactory>, Client> clientSupplier,
            BackoffStrategy backoffStrategy,
            Request.Options options,
            String userAgent) {
        this.contract = contract;
        this.encoder = encoder;
        this.decoder = new TraceResponseDecoder(decoder);
        this.errorDecoder = errorDecoder;
        this.clientSupplier = clientSupplier;
        this.backoffStrategy = backoffStrategy;
        this.options = options;
        this.userAgentInterceptor = UserAgentInterceptor.of(userAgent);
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
            Function<Optional<SSLSocketFactory>, Client> clientSupplier,
            String userAgent) {
        return new FeignClientFactory(contract, encoder, decoder, errorDecoder, clientSupplier,
                NeverRetryingBackoffStrategy.INSTANCE, new Request.Options(), userAgent);
    }

    /**
     * Returns a new instance using the specified contract/encoder/decoder/client when constructing clients.
     */
    public static FeignClientFactory of(
            Contract contract,
            Encoder encoder,
            Decoder decoder,
            ErrorDecoder errorDecoder,
            Function<Optional<SSLSocketFactory>, Client> clientSupplier,
            BackoffStrategy backoffStrategy,
            Request.Options options,
            String userAgent) {
        return new FeignClientFactory(
                contract, encoder, decoder, errorDecoder, clientSupplier, backoffStrategy, options, userAgent);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and feign {@link
     * feign.Client.Default} HTTP client.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, String uri, Class<T> type,
            Request.Options requestOptions) {
        return Feign.builder()
                .contract(contract)
                .encoder(encoder)
                .decoder(decoder)
                .requestInterceptor(TraceRequestInterceptor.INSTANCE)
                .errorDecoder(errorDecoder)
                .client(clientSupplier.apply(sslSocketFactory))
                .options(requestOptions)
                .logger(new Slf4jLogger(FeignClients.class))
                .logLevel(Level.BASIC)
                .requestInterceptor(userAgentInterceptor)
                .target(type, uri);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and feign {@link
     * feign.Client.Default} HTTP client.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, String uri, Class<T> type) {
        return createProxy(sslSocketFactory, uri, type, options);
    }


    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and feign {@link
     * feign.Client.Default} HTTP client. A {@link FailoverFeignTarget} is used to cycle through the given uris on
     * failure.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, Set<String> uris, Class<T> type,
            Request.Options requestOptions) {
        FailoverFeignTarget<T> target = new FailoverFeignTarget<>(uris, type, backoffStrategy);
        Client client = clientSupplier.apply(sslSocketFactory);
        client = target.wrapClient(client);
        return Feign.builder()
                .contract(contract)
                .encoder(encoder)
                .decoder(decoder)
                .requestInterceptor(TraceRequestInterceptor.INSTANCE)
                .errorDecoder(errorDecoder)
                .client(client)
                .retryer(target)
                .options(requestOptions)
                .logger(new Slf4jLogger(FeignClients.class))
                .logLevel(Level.BASIC)
                .requestInterceptor(userAgentInterceptor)
                .target(target);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and feign {@link
     * feign.Client.Default} HTTP client. A {@link FailoverFeignTarget} is used to cycle through the given uris on
     * failure.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, Set<String> uris, Class<T> type) {
        return createProxy(sslSocketFactory, uris, type, options);
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
        Optional<SslConfiguration> sslConfig = discoveryConfig.getSecurity(serviceName);

        if (sslConfig.isPresent()) {
            socketFactory = Optional.of(SslSocketFactories.createSslSocketFactory(sslConfig.get()));
        }

        Set<String> uris = Sets.newHashSet(serviceConfig.uris());
        Request.Options requestOptions = new Request.Options((int) serviceConfig.connectTimeout()
                .or(CONNECT_TIMEOUT).toMilliseconds(), (int) serviceConfig.readTimeout()
                .or(READ_TIMEOUT).toMilliseconds());
        return createProxy(socketFactory, uris, serviceClass, requestOptions);
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

    private static final Function<Optional<SSLSocketFactory>, Client> OKHTTP_CLIENT_SUPPLIER =
            new Function<Optional<SSLSocketFactory>, Client>() {
                @Override
                public Client apply(Optional<SSLSocketFactory> sslSocketFactory) {
                    com.squareup.okhttp.OkHttpClient client = new com.squareup.okhttp.OkHttpClient();
                    client.setSslSocketFactory(sslSocketFactory.orNull());
                    return new OkHttpClient(client);
                }
            };

    private static final Function<Optional<SSLSocketFactory>, Client> DEFAULT_CLIENT_SUPPLIER =
            new Function<Optional<SSLSocketFactory>, Client>() {
                @Override
                public Client apply(Optional<SSLSocketFactory> sslSocketFactory) {
                    return new Client.Default(sslSocketFactory.orNull(), null);
                }
            };

    /**
     * Supplies a feign {@link Client} wrapping a {@link com.squareup.okhttp.OkHttpClient} client with optionally
     * specified {@link SSLSocketFactory}.
     */
    public static Function<Optional<SSLSocketFactory>, Client> okHttpClient() {
        return OKHTTP_CLIENT_SUPPLIER;
    }

    /**
     * Supplies a feign {@link feign.Client.Default} client with default {@link javax.net.ssl.HostnameVerifier} and
     * optionally specified {@link SSLSocketFactory}.
     */
    public static Function<Optional<SSLSocketFactory>, Client> defaultClient() {
        return DEFAULT_CLIENT_SUPPLIER;
    }

}
