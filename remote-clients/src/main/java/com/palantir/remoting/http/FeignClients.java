/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import com.palantir.remoting.http.errors.SerializableErrorErrorDecoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JAXRSContract;

/**
 * Static factory methods for producing common configurations of {@link FeignClientFactory}, which in turn may be used
 * to create HTTP proxies for HTTP remoting clients.
 */
public final class FeignClients {

    private FeignClients() {}

    /**
     * Provides a {@link FeignClientFactory} with an {@link com.fasterxml.jackson.databind.ObjectMapper} configured with
     * {@link com.fasterxml.jackson.datatype.guava.GuavaModule} and
     * {@link com.fasterxml.jackson.datatype.jdk7.Jdk7Module}.
     */
    public static FeignClientFactory standard() {
        return FeignClientFactory.of(
                new JAXRSContract(),
                new JacksonEncoder(ObjectMappers.guavaJdk7()),
                new JacksonDecoder(ObjectMappers.guavaJdk7()),
                new SerializableErrorErrorDecoder(),
                FeignClientFactory.okHttpClient());
    }

    /**
     * Provides a {@link FeignClientFactory} with an unmodified {@link com.fasterxml.jackson.databind.ObjectMapper}.
     */
    public static FeignClientFactory vanilla() {
        return FeignClientFactory.of(
                new JAXRSContract(),
                new JacksonEncoder(ObjectMappers.vanilla()),
                new JacksonDecoder(ObjectMappers.vanilla()),
                new SerializableErrorErrorDecoder(),
                FeignClientFactory.okHttpClient());
    }

}
