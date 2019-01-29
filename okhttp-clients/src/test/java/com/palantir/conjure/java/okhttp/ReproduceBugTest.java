/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ReproduceBugTest extends TestBase {

    @Rule
    public final MockWebServer server = new MockWebServer();

    private final HostMetricsRegistry hostEventsSink = new HostMetricsRegistry();

    private String slowUrl;

    @Before
    public void before() {
        slowUrl = "http://localhost:" + server.getPort();
        System.out.println("slowUrl" + slowUrl);
    }

    private static final int CLIENTS = 200;

    @Test
    public void dispatcher_behaves_weirdly() throws IOException, InterruptedException {

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.dispatcher(OkHttpClients.dispatcher);
        OkHttpClient client = builder.build();

        for (int i = 0; i < CLIENTS; i ++) {
            server.enqueue(new MockResponse()
                    .setBodyDelay(10, TimeUnit.SECONDS)
                    .setResponseCode(200)
                    .setBody("I am slow"));
        }

        CountDownLatch started = new CountDownLatch(CLIENTS);

        ExecutorService executor = Executors.newFixedThreadPool(CLIENTS);
        IntStream.range(0, CLIENTS).forEach(i -> {
            executor.submit(() -> {
                started.countDown();
                Call call = client.newCall(new Request.Builder().url(slowUrl + "/").build());
                try {
                    System.out.println(i + ": " + call.execute().body().string());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        });

        assertThat(started.await(5, TimeUnit.SECONDS)).describedAs("All clients must start").isTrue();

        System.out.println("Sending request to fast server");
        Stopwatch sw = Stopwatch.createStarted();

        Call call = client.newCall(new Request.Builder().url("http://www.echojs.com/").build());
        String fastResponse = call.execute().body().string();

        long time = sw.elapsed(TimeUnit.MILLISECONDS);

        System.out.println("poop: " + fastResponse + " " + time);
        assertThat(time).isLessThan(3_000);

        executor.shutdown();
    }
}
