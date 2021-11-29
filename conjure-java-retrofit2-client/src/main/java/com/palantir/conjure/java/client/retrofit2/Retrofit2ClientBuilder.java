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

package com.palantir.conjure.java.client.retrofit2;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.okhttp.HostEventsSink;
import com.palantir.conjure.java.okhttp.NoOpHostEventsSink;
import com.palantir.conjure.java.okhttp.OkHttpClients;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.Preconditions;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public final class Retrofit2ClientBuilder {
    private static final JsonMapper JSON_MAPPER = ObjectMappers.newClientJsonMapper();
    private static final CBORMapper CBOR_MAPPER = ObjectMappers.newClientCborMapper();

    private ClientConfiguration config;

    public Retrofit2ClientBuilder(ClientConfiguration config) {
        Preconditions.checkArgument(!config.uris().isEmpty(), "Cannot construct retrofit client with empty URI list");
        this.config = config;
    }

    /** Set the host metrics registry to use when constructing the OkHttp client. */
    public Retrofit2ClientBuilder hostEventsSink(HostEventsSink newHostEventsSink) {
        Preconditions.checkNotNull(newHostEventsSink, "hostEventsSink can't be null");
        config = ClientConfiguration.builder()
                .from(config)
                .hostEventsSink(newHostEventsSink)
                .build();
        return this;
    }

    public <T> T build(Class<T> serviceClass, UserAgent userAgent) {
        okhttp3.OkHttpClient client = OkHttpClients.create(
                config,
                userAgent,
                config.hostEventsSink().map(HostEventsSink::from).orElse(NoOpHostEventsSink.INSTANCE),
                serviceClass);

        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(addTrailingSlash(config.uris().get(0)))
                // These get evaluated first, but only for successful responses that are not 204 or 205
                .addConverterFactory(OptionalResponseBodyConverterFactory.INSTANCE)
                .addConverterFactory(new CborConverterFactory(
                        new NeverReturnNullConverterFactory(
                                new CoerceNullValuesConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))),
                        CBOR_MAPPER))
                .addConverterFactory(OptionalObjectToStringConverterFactory.INSTANCE)
                // These get evaluated last, to convert the original Call into the response type expected by the client
                .addCallAdapterFactory(new QosExceptionThrowingCallAdapterFactory(
                        new CoerceNullValuesCallAdapterFactory(AsyncSerializableErrorCallAdapterFactory.INSTANCE)))
                .build();
        return retrofit.create(serviceClass);
    }

    private static String addTrailingSlash(String url) {
        return url.charAt(url.length() - 1) == '/' ? url : url + "/";
    }
}
