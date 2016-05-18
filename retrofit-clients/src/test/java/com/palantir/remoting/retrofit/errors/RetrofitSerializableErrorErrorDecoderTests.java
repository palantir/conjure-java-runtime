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

package com.palantir.remoting.retrofit.errors;

import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import org.junit.Test;
import retrofit.RetrofitError;
import retrofit.client.Header;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.mime.TypedString;

public final class RetrofitSerializableErrorErrorDecoderTests {

    private static final RetrofitSerializableErrorErrorHandler decoder = RetrofitSerializableErrorErrorHandler.INSTANCE;

    @Test
    public void testSanity() {
        // most tests are in SerializableErrorErrorDecoderTests
        Response response =
                new Response("url", 400, "reason", ImmutableList.of(new Header("Content-Type", "text/plain")),
                        new TypedString("errorbody"));
        RetrofitError retrofitError =
                RetrofitError.httpError("url", response, new GsonConverter(new Gson()), String.class);
        Throwable error = decoder.handleError(retrofitError);
        assertThat(error, is(instanceOf(RuntimeException.class)));
        assertThat(error.getMessage(), is("Error 400. Reason: reason. Body:\nerrorbody"));
    }

    @Test
    public void testNoContentType() {
        Response response =
                new Response("url", 400, "reason", ImmutableList.<Header>of(), new TypedString("errorbody"));
        RetrofitError retrofitError =
                RetrofitError.httpError("url", response, new GsonConverter(new Gson()), String.class);
        Throwable error = decoder.handleError(retrofitError);
        assertThat(error, is(instanceOf(RuntimeException.class)));
        assertThat(error.getMessage(),
                is("Error 400. Reason: reason. Body content type: []. Body as String: errorbody"));
    }
}
