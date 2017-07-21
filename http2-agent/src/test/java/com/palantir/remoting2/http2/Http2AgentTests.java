/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting3.http2;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.Arrays;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.BeforeClass;
import org.junit.Test;

public final class Http2AgentTests {

    @BeforeClass
    public static void beforeClass() {
        Http2Agent.install();
    }

    @Test
    public void testHttp2Support() throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .protocols(Arrays.asList(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .build();

        Request request = new Request.Builder()
                .url("https://http2.akamai.com/demo")
                .build();

        Response response = client.newCall(request).execute();

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.protocol()).isEqualTo(Protocol.HTTP_2);
    }

    @Test
    public void testHttp1_1Support() throws IOException {
        OkHttpClient client = new OkHttpClient.Builder()
                .protocols(Arrays.asList(Protocol.HTTP_1_1))
                .build();

        Request request = new Request.Builder()
                .url("https://http2.akamai.com/demo")
                .build();

        Response response = client.newCall(request).execute();

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.protocol()).isEqualTo(Protocol.HTTP_1_1);
    }

}
