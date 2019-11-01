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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.client.config.ClientConfigurations;
import com.palantir.conjure.java.config.ssl.SslSocketFactories;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public abstract class TestBase {

    protected static final UserAgent AGENT = UserAgent.of(UserAgent.Agent.of("test", "0.0.1"));

    @SafeVarargs
    static <T> Set<T> set(T... items) {
        return ImmutableSet.copyOf(items);
    }

    @SafeVarargs
    static <T> List<T> list(T... items) {
        return ImmutableList.copyOf(items);
    }

    protected final ClientConfiguration createTestConfig(String... uri) {
        SslConfiguration sslConfig = SslConfiguration.of(Paths.get("src/test/resources/trustStore.jks"));
        return ClientConfigurations.of(
                ImmutableList.copyOf(uri),
                SslSocketFactories.createSslSocketFactory(sslConfig),
                SslSocketFactories.createX509TrustManager(sslConfig));
    }

    protected static Response responseWithCode(Request request, int code) {
        return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("unused").build();
    }
}
