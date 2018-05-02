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

package com.palantir.remoting3.retrofit2;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.palantir.remoting3.clients.ClientConfiguration;
import com.palantir.remoting3.clients.UserAgent;
import com.palantir.remoting3.clients.UserAgents;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import com.palantir.remoting3.okhttp.OkHttpClients;
import java.util.function.UnaryOperator;
import okhttp3.OkHttpClient.Builder;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public final class Retrofit2ClientBuilder {
    private static final ObjectMapper CBOR_OBJECT_MAPPER = ObjectMappers.newCborClientObjectMapper();
    private static final ObjectMapper OBJECT_MAPPER = ObjectMappers.newClientObjectMapper();

    private final ClientConfiguration config;

    public Retrofit2ClientBuilder(ClientConfiguration config) {
        Preconditions.checkArgument(!config.uris().isEmpty(), "Cannot construct retrofit client with empty URI list");
        this.config = config;
    }

    /**
     * @deprecated Use {@link #build(Class, UserAgent)}.
     */
    @Deprecated
    public <T> T build(Class<T> serviceClass, String userAgent) {
        return build(serviceClass, UserAgents.tryParse(userAgent));
    }

    public <T> T build(Class<T> serviceClass, UserAgent userAgent) {
        return build(serviceClass, userAgent, UnaryOperator.identity());
    }

    public <T> T build(Class<T> serviceClass, UserAgent userAgent, UnaryOperator<Builder> builderExtraConfiguration) {
        okhttp3.OkHttpClient client = OkHttpClients.create(config, userAgent, serviceClass, builderExtraConfiguration);
        Retrofit retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(addTrailingSlash(config.uris().get(0)))
                .addConverterFactory(new CborConverterFactory(
                        JacksonConverterFactory.create(OBJECT_MAPPER),
                        CBOR_OBJECT_MAPPER))
                .addConverterFactory(OptionalObjectToStringConverterFactory.INSTANCE)
                .addCallAdapterFactory(AsyncSerializableErrorCallAdapterFactory.INSTANCE)
                .build();
        return retrofit.create(serviceClass);
    }

    private static String addTrailingSlash(String url) {
        return url.charAt(url.length() - 1) == '/' ? url : url + "/";
    }

}
