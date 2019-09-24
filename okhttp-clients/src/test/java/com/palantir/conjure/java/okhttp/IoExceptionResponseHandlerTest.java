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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class IoExceptionResponseHandlerTest {

    private static final int STATUS_500 = 500;
    private static final Request request = new Request.Builder().url("http://url").build();

    private static final IoExceptionResponseHandler handler = IoExceptionResponseHandler.INSTANCE;

    @Test
    public void doesNotProduceExceptionOn101Or2xx() {
        assertThat(handler.handle(response(200, MediaType.APPLICATION_JSON, "body"))).isEmpty();
        assertThat(handler.handle(response(101, MediaType.APPLICATION_JSON, "body"))).isEmpty();
    }

    @Test
    public void extractsIoExceptionForAllErrorCodes() {
        for (int code : ImmutableList.of(300, 400, 404, 500)) {
            IOException exception = decode(MediaType.APPLICATION_JSON, code, "body").get();
            assertThat(exception.getMessage()).isEqualTo(
                    "Failed to parse response body as SerializableError: "
                            + "{code="
                            + code
                            + ", body=body, contentType=application/json}");
        }
    }

    @Test
    public void handlesNonJsonMediaTypes() {
        assertThat(decode(MediaType.TEXT_PLAIN, STATUS_500, "body")).isNotEmpty();
        assertThat(decode(MediaType.TEXT_HTML, STATUS_500, "body")).isNotEmpty();
        assertThat(decode(MediaType.MULTIPART_FORM_DATA, STATUS_500, "body")).isNotEmpty();
    }

    @Test
    public void handlesNullBody() {
        assertThat(decode(MediaType.APPLICATION_JSON, STATUS_500, null)).isNotEmpty();
    }

    private static Optional<IOException> decode(String contentType, int status, @CheckForNull String body) {
        return handler.handle(response(status, contentType, body));
    }

    private static Response response(int code, String mediaType, @CheckForNull String body) {
        Response.Builder response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("unused")
                .header(HttpHeaders.CONTENT_TYPE, mediaType);
        if (body != null) {
            response.body(ResponseBody.create(okhttp3.MediaType.parse(mediaType), body));
        }
        return response.build();
    }
}
