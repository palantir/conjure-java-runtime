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

package com.palantir.remoting.http.errors;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.palantir.remoting.http.FeignClientFactory;
import com.palantir.remoting.http.GuavaOptionalAwareContract;
import com.palantir.remoting.http.NeverRetryingBackoffStrategy;
import com.palantir.remoting.http.ObjectMappers;
import com.palantir.remoting.http.SlashEncodingContract;
import feign.InputStreamDelegateDecoder;
import feign.InputStreamDelegateEncoder;
import feign.OptionalAwareDecoder;
import feign.Request;
import feign.TextDelegateDecoder;
import feign.TextDelegateEncoder;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import feign.jaxrs.JaxRsWithHeaderAndQueryMapContract;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.junit.Test;

public final class GoErrorToExceptionConverterTests {

    @Test
    public void testOk() {
        TestServer testServer = createTestServerWithGoHandler();
        assertThat(testServer.ok()).isEqualTo("hello, world!");
    }

    @Test
    public void testSimpleStackTraceError() {
        TestServer testServer = createTestServerWithGoHandler();

        try {
            testServer.simpleStackTraceError();
            fail();
        } catch (GoException e) {
            assertThat(e).hasMessage("Error 500. Reason: Internal Server Error. Cause: simpleError error message");
            assertThat(e.getStackTrace()).isEqualTo(new StackTraceElement[] {
                    new StackTraceElement("<no receiver>", "simpleStackTraceError", "app/server.go", 65)
            });

            assertThat(e.getCause()).isInstanceOf(GoException.class);
            assertThat(e.getCause()).hasMessage("errors.New error message");
            assertThat(e.getCause().getStackTrace()).isEmpty();

            assertThat(e.getCause().getCause()).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    public void testNestedStackTraceError() {
        TestServer testServer = createTestServerWithGoHandler();

        try {
            testServer.nestedStackTraceError();
            fail();
        } catch (GoException e) {
            assertThat(e).hasMessage("Error 500. Reason: Internal Server Error. Cause: outerError error message");
            assertThat(e.getStackTrace()).isEqualTo(new StackTraceElement[] {
                    new StackTraceElement("<no receiver>", "outerStackTraceError", "app/server.go", 70)
            });

            assertThat(e.getCause()).isInstanceOf(GoException.class);
            assertThat(e.getCause()).hasMessage("simpleError error message");
            assertThat(e.getCause().getStackTrace()).isEqualTo(new StackTraceElement[] {
                    new StackTraceElement("<no receiver>", "simpleStackTraceError", "app/server.go", 65)
            });

            assertThat(e.getCause().getCause()).isInstanceOf(GoException.class);
            assertThat(e.getCause().getCause()).hasMessage("errors.New error message");
            assertThat(e.getCause().getCause().getStackTrace()).isEmpty();

            assertThat(e.getCause().getCause().getCause()).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    public void testNestedNoMessageStackTraceError() {
        TestServer testServer = createTestServerWithGoHandler();

        try {
            testServer.nestedNoMessageStackTraceError();
            fail();
        } catch (GoException e) {
            assertThat(e).hasMessage("Error 500. Reason: Internal Server Error.");
            assertThat(e.getStackTrace()).isEqualTo(new StackTraceElement[] {
                    new StackTraceElement("<no receiver>", "outerNoMessageStackTraceError", "app/server.go", 80),
                    new StackTraceElement("<no receiver>", "noMessageStackTraceError", "app/server.go", 75)
            });

            assertThat(e.getCause()).isInstanceOf(GoException.class);
            assertThat(e.getCause()).hasMessage("errors.New error message");
            assertThat(e.getCause().getStackTrace()).isEmpty();

            assertThat(e.getCause().getCause()).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    public void testInterfaceFunctionStackTraceError() {
        TestServer testServer = createTestServerWithGoHandler();

        try {
            testServer.interfaceFunctionStackTraceError();
            fail();
        } catch (GoException e) {
            assertThat(e).hasMessage("Error 500. Reason: Internal Server Error.");
            assertThat(e.getStackTrace()).isEqualTo(new StackTraceElement[] {
                    new StackTraceElement("<no receiver>", "interfaceFunctionStackTraceError", "app/server.go", 85)
            });

            assertThat(e.getCause()).isInstanceOf(GoException.class);
            assertThat(e.getCause()).hasMessage("interface method error");
            assertThat(e.getCause().getStackTrace()).isEqualTo(new StackTraceElement[] {
                    new StackTraceElement("TestInterface", "interfaceError", "app/server.go", 116)
            });

            assertThat(e.getCause().getCause()).isInstanceOf(GoException.class);
            assertThat(e.getCause().getCause()).hasMessage("errors.New error message");

            assertThat(e.getCause().getCause().getCause()).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    public void testNativeError() {
        TestServer testServer = createTestServerWithGoHandler();

        try {
            testServer.nativeError();
            fail();
        } catch (GoException e) {
            assertThat(e).hasMessage("Error 500. Reason: Internal Server Error. Cause: errors.New error message");
            assertThat(e.getStackTrace()).isEmpty();

            assertThat(e.getCause()).isInstanceOf(RuntimeException.class);
        }
    }

    @Test
    public void testPanic() {
        TestServer testServer = createTestServerWithGoHandler();

        try {
            testServer.panic();
            fail();
        } catch (RuntimeException e) {
            assertThat(e).isInstanceOf(RuntimeException.class);
            assertThat(e.getMessage()).contains("Error 500. Reason: Internal Server Error. "
                    + "Body content type: [text/plain; charset=utf-8]. Body as String: PANIC: panicking in the server");
        }
    }

    private TestServer createTestServerWithGoHandler() {
        return getTestFactory().createProxy(Optional.<SSLSocketFactory>absent(),
                "http://localhost:3000", TestServer.class);
    }

    private FeignClientFactory getTestFactory() {
        return FeignClientFactory.of(
                new SlashEncodingContract(new GuavaOptionalAwareContract(new JaxRsWithHeaderAndQueryMapContract())),
                new InputStreamDelegateEncoder(new TextDelegateEncoder(new JacksonEncoder(ObjectMappers.guavaJdk7()))),
                new OptionalAwareDecoder(new InputStreamDelegateDecoder(
                        new TextDelegateDecoder(new JacksonDecoder(ObjectMappers.guavaJdk7())))),
                ErrorDecoderImpl.GO_ERROR,
                FeignClientFactory.okHttpClient(),
                NeverRetryingBackoffStrategy.INSTANCE,
                new Request.Options());
    }

    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    private interface TestServer {
        @GET
        @Path("/ok")
        String ok();

        @GET
        @Path("/simpleStackTraceError")
        String simpleStackTraceError();

        @GET
        @Path("/nestedStackTraceError")
        String nestedStackTraceError();

        @GET
        @Path("/nestedNoMessageStackTraceError")
        String nestedNoMessageStackTraceError();

        @GET
        @Path("/interfaceFunctionStackTraceError")
        String interfaceFunctionStackTraceError();

        @GET
        @Path("/nativeError")
        String nativeError();

        @GET
        @Path("/panic")
        String panic();
    }
}
