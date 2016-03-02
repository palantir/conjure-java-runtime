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
import com.google.common.collect.Lists;
import feign.Client;
import feign.Contract;
import feign.Feign;
import feign.Logger.Level;
import feign.Request;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.net.ssl.SSLSocketFactory;

/**
 * Factory for initializing Feign-based HTTP-invoking dynamic proxies around service interfaces.
 */
public final class FeignClientFactory {

    private final Contract contract;
    private final Encoder encoder;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;
    private final Function<Optional<SSLSocketFactory>, Client> clientSupplier;
    private final BackoffStrategy backoffStrategy;
    private final Request.Options options;

    private FeignClientFactory(
            Contract contract,
            Encoder encoder,
            Decoder decoder,
            ErrorDecoder errorDecoder,
            Function<Optional<SSLSocketFactory>, Client> clientSupplier,
            BackoffStrategy backoffStrategy,
            Request.Options options) {
        this.contract = contract;
        this.encoder = encoder;
        this.decoder = decoder;
        this.errorDecoder = errorDecoder;
        this.clientSupplier = clientSupplier;
        this.backoffStrategy = backoffStrategy;
        this.options = options;
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
            Function<Optional<SSLSocketFactory>, Client> clientSupplier) {
        return new FeignClientFactory(contract, encoder, decoder, errorDecoder, clientSupplier,
                NeverRetryingBackoffStrategy.INSTANCE, new Request.Options());
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
            Request.Options options) {
        return new FeignClientFactory(
                contract, encoder, decoder, errorDecoder, clientSupplier, backoffStrategy, options);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and feign {@link
     * feign.Client.Default} HTTP client.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, String uri, Class<T> type) {
        return Feign.builder()
                .contract(contract)
                .encoder(encoder)
                .decoder(decoder)
                .errorDecoder(errorDecoder)
                .client(clientSupplier.apply(sslSocketFactory))
                .options(options)
                .logger(new Slf4jLogger(FeignClients.class))
                .logLevel(Level.BASIC)
                .target(type, uri);
    }

    /**
     * Constructs a dynamic proxy for the specified type, using the supplied SSL factory if is present, and feign {@link
     * feign.Client.Default} HTTP client. A {@link FailoverFeignTarget} is used to cycle through the given uris on
     * failure.
     */
    public <T> T createProxy(Optional<SSLSocketFactory> sslSocketFactory, Set<String> uris, Class<T> type) {
        FailoverFeignTarget<T> target = new FailoverFeignTarget<>(uris, type, backoffStrategy);
        Client client = clientSupplier.apply(sslSocketFactory);
        client = target.wrapClient(client);
        return Feign.builder()
                .contract(contract)
                .encoder(encoder)
                .decoder(decoder)
                .errorDecoder(errorDecoder)
                .client(client)
                .retryer(target)
                .options(options)
                .logger(new Slf4jLogger(FeignClients.class))
                .logLevel(Level.BASIC)
                .target(target);
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
