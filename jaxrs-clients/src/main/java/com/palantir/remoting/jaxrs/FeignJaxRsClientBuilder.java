/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.jaxrs;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.okhttp.BraveOkHttpRequestResponseInterceptor;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.net.HttpHeaders;
import com.google.common.net.InetAddresses;
import com.palantir.config.service.BasicCredentials;
import com.palantir.config.service.ProxyConfiguration;
import com.palantir.ext.brave.SlfLoggingSpanCollector;
import com.palantir.remoting.http.BackoffStrategy;
import com.palantir.remoting.http.FailoverFeignTarget;
import com.palantir.remoting.http.GuavaOptionalAwareContract;
import com.palantir.remoting.http.Jackson24Encoder;
import com.palantir.remoting.http.NeverRetryingBackoffStrategy;
import com.palantir.remoting.http.ObjectMappers;
import com.palantir.remoting.http.SlashEncodingContract;
import com.palantir.remoting.http.UserAgentInterceptor;
import com.palantir.remoting.http.errors.FeignSerializableErrorErrorDecoder;
import feign.Contract;
import feign.Feign;
import feign.InputStreamDelegateDecoder;
import feign.InputStreamDelegateEncoder;
import feign.Logger;
import feign.OptionalAwareDecoder;
import feign.Request;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JaxRsWithHeaderAndQueryMapContract;
import feign.okhttp3.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Response;
import okhttp3.Route;
import org.joda.time.Duration;

public final class FeignJaxRsClientBuilder extends ClientBuilder {

    private static final Duration CONNECT_TIMEOUT = Duration.standardMinutes(10);
    private static final Duration READ_TIMEOUT = Duration.standardMinutes(10);

    private final BackoffStrategy backoffStrategy;
    private final JaxRsClientConfig config;

    @VisibleForTesting
    FeignJaxRsClientBuilder(JaxRsClientConfig config, BackoffStrategy backoffStrategy) {
        this.backoffStrategy = backoffStrategy;
        this.config = config;
    }

    FeignJaxRsClientBuilder(JaxRsClientConfig config) {
        this.config = config;
        this.backoffStrategy = NeverRetryingBackoffStrategy.INSTANCE;
    }

    @Override
    public <T> T build(Class<T> serviceClass, String userAgent, List<String> uris) {
        FailoverFeignTarget<T> target = createTarget(serviceClass, uris);
        ObjectMapper objectMapper = ObjectMappers.guavaJdk7();
        return Feign.builder()
                .contract(createContract())
                .encoder(createEncoder(objectMapper))
                .decoder(createDecoder(objectMapper))
                .errorDecoder(FeignSerializableErrorErrorDecoder.INSTANCE)
                .client(target.wrapClient(createOkHttpClient(userAgent)))
                .retryer(target)
                .options(createRequestOptions())
                .logger(new Slf4jLogger(JaxRsClient.class))
                .logLevel(Logger.Level.BASIC)
                .requestInterceptor(UserAgentInterceptor.of(userAgent))
                .target(target);
    }

    private <T> FailoverFeignTarget<T> createTarget(Class<T> serviceClass, List<String> uris) {
        return new FailoverFeignTarget<>(uris, serviceClass, backoffStrategy);
    }

    private Contract createContract() {
        return new SlashEncodingContract(
                new GuavaOptionalAwareContract(new JaxRsWithHeaderAndQueryMapContract()));
    }

    private Request.Options createRequestOptions() {
        return new Request.Options(
                (int) config.getConnectTimeout().or(CONNECT_TIMEOUT).getMillis(),
                (int) config.getReadTimeout().or(READ_TIMEOUT).getMillis());
    }

    private Encoder createEncoder(ObjectMapper objectMapper) {
        Encoder jacksonEncoder = hasJackson25()
                ? new JacksonEncoder(objectMapper)
                : new Jackson24Encoder(objectMapper);
        return new InputStreamDelegateEncoder(new TextDelegateEncoder(jacksonEncoder));
    }

    // Uses reflection to determine if Jackson >= 2.5 is on the classpath by checking for the existence of the
    // ObjectMapper#writerFor method.
    private boolean hasJackson25() {
        try {
            ObjectMapper.class.getMethod("writerFor", JavaType.class);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Decoder createDecoder(ObjectMapper objectMapper) {
        return new OptionalAwareDecoder(
                new InputStreamDelegateDecoder(new TextDelegateDecoder(new JacksonDecoder(objectMapper))));
    }

    private feign.Client createOkHttpClient(String userAgent) {
        okhttp3.OkHttpClient.Builder client = new okhttp3.OkHttpClient.Builder();
        if (config.getSslSocketFactory().isPresent()) {
            client.sslSocketFactory(config.getSslSocketFactory().get());
        }

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

        // Set up HTTP proxy configuration
        if (config.getProxyConfiguration().isPresent()) {
            ProxyConfiguration proxy = config.getProxyConfiguration().get();
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

        return new OkHttpClient(client.build());
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
