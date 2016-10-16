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

package com.palantir.remoting1.jaxrs;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.kristofa.brave.AnnotationSubmitter;
import com.github.kristofa.brave.ClientRequestInterceptor;
import com.github.kristofa.brave.ClientResponseInterceptor;
import com.github.kristofa.brave.ClientTracer;
import com.github.kristofa.brave.Sampler;
import com.github.kristofa.brave.ThreadLocalServerClientAndLocalSpanState;
import com.github.kristofa.brave.ext.SlfLoggingSpanCollector;
import com.github.kristofa.brave.http.DefaultSpanNameProvider;
import com.github.kristofa.brave.okhttp.BraveOkHttpRequestResponseInterceptor;
import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.common.net.InetAddresses;
import com.palantir.remoting1.clients.ClientBuilder;
import com.palantir.remoting1.clients.ClientConfig;
import com.palantir.remoting1.config.service.BasicCredentials;
import com.palantir.remoting1.config.service.ProxyConfiguration;
import com.palantir.remoting1.config.ssl.TrustContext;
import com.palantir.remoting1.jaxrs.feignimpl.FailoverFeignTarget;
import com.palantir.remoting1.jaxrs.feignimpl.FeignSerializableErrorErrorDecoder;
import com.palantir.remoting1.jaxrs.feignimpl.GuavaOptionalAwareContract;
import com.palantir.remoting1.jaxrs.feignimpl.Jackson24Encoder;
import com.palantir.remoting1.jaxrs.feignimpl.NeverRetryingBackoffStrategy;
import com.palantir.remoting1.jaxrs.feignimpl.ObjectMappers;
import com.palantir.remoting1.jaxrs.feignimpl.SlashEncodingContract;
import com.palantir.remoting1.jaxrs.feignimpl.UserAgentInterceptor;
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
import feign.okhttp.OkHttpClient;
import feign.slf4j.Slf4jLogger;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import okhttp3.Authenticator;
import okhttp3.Credentials;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.internal.JavaNetCookieJar;

public final class FeignJaxRsClientBuilder extends ClientBuilder {

    private final ClientConfig config;

    FeignJaxRsClientBuilder(ClientConfig config) {
        this.config = config;
        Preconditions.checkArgument(config.maxNumRetries() == 0,
                "Connection-level retries are not supported by %s", JaxRsClient.class.getSimpleName());
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
        return new FailoverFeignTarget<>(uris, serviceClass, NeverRetryingBackoffStrategy.INSTANCE);
    }

    private Contract createContract() {
        return new SlashEncodingContract(
                new GuavaOptionalAwareContract(new JaxRsWithHeaderAndQueryMapContract()));
    }

    private Request.Options createRequestOptions() {
        return new Request.Options(
                (int) config.connectTimeout().toMilliseconds(),
                (int) config.readTimeout().toMilliseconds());
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

        // SSL
        if (config.trustContext().isPresent()) {
            TrustContext context = config.trustContext().get();
            client.sslSocketFactory(context.sslSocketFactory(), context.x509TrustManager());
        }

        // timeouts
        // Note that Feign overrides OkHttp timeouts with the timeouts given in FeignBuilder#Options if given, or
        // with its own default otherwise. Feign does not provide a mechanism for write timeouts. We thus need to set
        // write timeouts here and connect&read timeouts on FeignBuilder.
        client.writeTimeout(config.writeTimeout().toMilliseconds(), TimeUnit.MILLISECONDS);

        // Set up Zipkin/Brave tracing
        ClientTracer tracer = ClientTracer.builder()
                .traceSampler(Sampler.ALWAYS_SAMPLE)
                .randomGenerator(new Random())
                .state(new ThreadLocalServerClientAndLocalSpanState(
                        getIpAddress(), 0 /* Client TCP port. */, userAgent))
                .spanCollector(new SlfLoggingSpanCollector("tracing.client." + userAgent))
                .clock(new AnnotationSubmitter.Clock() {
                    @Override
                    public long currentTimeMicroseconds() {
                        return TimeUnit.MICROSECONDS.convert(System.nanoTime(), TimeUnit.NANOSECONDS);
                    }
                })
                .build();
        BraveOkHttpRequestResponseInterceptor braveInterceptor =
                new BraveOkHttpRequestResponseInterceptor(
                        new ClientRequestInterceptor(tracer),
                        new ClientResponseInterceptor(tracer),
                        new DefaultSpanNameProvider());
        client.addInterceptor(braveInterceptor);

        // Set up HTTP proxy configuration
        if (config.proxy().isPresent()) {
            ProxyConfiguration proxy = config.proxy().get();
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

        // cookies
        CookieManager cm = new CookieManager(null, new CookiePolicy() {

            @Override
            public boolean shouldAccept(URI uri, HttpCookie cookie) {
                return isAllowedUri(uri)
                        && isAllowedCookieName(cookie);
            }

            private boolean isAllowedUri(URI uri) {
                return config.allowedCookieHosts().isPresent()
                        && config.allowedCookieHosts().get().contains(uri.getHost());
            }

            private boolean isAllowedCookieName(HttpCookie cookie) {
                if (config.allowedCookieNameRegex().isPresent()) {
                    for (String nameRegex : config.allowedCookieNameRegex().get()) {
                        if (cookie.getName().matches(nameRegex)) {
                            return true;
                        }
                    }
                }
                return false;
            }

        });

        client.cookieJar(new JavaNetCookieJar(cm));


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
