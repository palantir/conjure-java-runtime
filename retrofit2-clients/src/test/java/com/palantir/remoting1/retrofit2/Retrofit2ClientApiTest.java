/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.retrofit2;


import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class Retrofit2ClientApiTest {
    @Rule
    public final MockWebServer server = new MockWebServer();

    private HttpUrl url;

    @Before
    public void before() {
        url = server.url("/");
    }

    @Test
    public void testOptionalHandling() throws IOException, InterruptedException {
        TestService service = Retrofit2Client.builder()
                .build(TestService.class, "agent", ImmutableList.of(url.toString()));

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getGuavaOptionalString(guavaOptional("p"), guavaOptional("q")).execute().body())
                .isEqualTo(guavaOptional("pong"));
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/getGuavaOptionalString/p/?queryString=q");

        server.enqueue(new MockResponse().setBody("\"pong\""));
        assertThat(service.getJava8OptionalString(java8Optional("p"), java8Optional("q")).execute().body())
                .isEqualTo(java8Optional("pong"));
        assertThat(server.takeRequest().getPath()).isEqualTo("/getJava8OptionalString/p/?queryString=q");
    }

    private static <T> com.google.common.base.Optional<T> guavaOptional(T value) {
        return com.google.common.base.Optional.of(value);
    }

    private static <T> java.util.Optional<T> java8Optional(T value) {
        return java.util.Optional.of(value);
    }
}
