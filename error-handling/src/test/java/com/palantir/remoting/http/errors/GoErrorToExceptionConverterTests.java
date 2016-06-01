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

import com.google.common.collect.ImmutableSet;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.ws.rs.core.MediaType;
import org.eclipse.jetty.http.HttpStatus;
import org.junit.Test;

public final class GoErrorToExceptionConverterTests {

    @Test
    public void testGetExceptionEmptyJson() {
        InputStream body = new ByteArrayInputStream("\"\"".getBytes(StandardCharsets.UTF_8));
        Exception exception = GoErrorToExceptionConverter.getException(ImmutableSet.of(MediaType.APPLICATION_JSON),
                HttpStatus.OK_200, "testReason", body);

        assertThat(exception).isInstanceOf(GoException.class);
        assertThat(exception).hasMessage("Error 200. Reason: testReason.");
    }

    @Test
    public void testGetExceptionFallsBackIfJsonInvalid() {
        InputStream body = new ByteArrayInputStream("invalidJson".getBytes(StandardCharsets.UTF_8));
        Exception exception = GoErrorToExceptionConverter.getException(ImmutableSet.of(MediaType.APPLICATION_JSON),
                HttpStatus.OK_200, "testReason", body);

        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).contains(
                "Failed to parse error body and instantiate exception: Unrecognized token 'invalidJson'");
    }

    @Test
    public void testParseExceptionNoTopLevelCause() throws Exception {
        String error = "\"--- at /test/test.go:100 (Test.Find) ---\\n --- at /test/test.go:13 (Find) ---\\n --- "
                + "at /test/test.go:90 (Test.Function) ---\\nCaused by: foo not found\\n --- at /test/test.go:148 "
                + "(Test.AnotherFunction) ---\"";
        InputStream body = new ByteArrayInputStream(error.getBytes(StandardCharsets.UTF_8));
        Exception exception = GoErrorToExceptionConverter.getException(ImmutableSet.of(MediaType.APPLICATION_JSON),
                HttpStatus.BAD_REQUEST_400, "testReason", body);

        // verify outer exception
        assertThat(exception).isInstanceOf(GoException.class);
        assertThat(exception).hasMessage("Error 400. Reason: testReason.");
        assertThat(exception.getStackTrace()).isEqualTo(
                new StackTraceElement[] {
                        new StackTraceElement("Test", "Find", "/test/test.go", 100),
                        new StackTraceElement("<no receiver>", "Find", "/test/test.go", 13),
                        new StackTraceElement("Test", "Function", "/test/test.go", 90)
                });

        // verify first-level Go cause
        assertThat(exception.getCause()).isInstanceOf(GoException.class);
        assertThat(exception.getCause()).hasMessage("foo not found");
        assertThat(exception.getCause().getStackTrace()).isEqualTo(
                new StackTraceElement[] {new StackTraceElement("Test", "AnotherFunction", "/test/test.go", 148)});

        // verify second-level Java cause
        assertThat(exception.getCause().getCause()).isInstanceOf(RuntimeException.class);
        assertThat(exception.getCause().getCause().getMessage()).isNull();
    }

    @Test
    public void testParseExceptionWithTopLevelCause() {
        String error = "\"Failed to register for villain discovery\\n"
                + " --- at github.com/palantir/shield/agent/discovery.go:265 (ShieldAgent.reallyRegister) ---\\n"
                + " --- at github.com/palantir/shield/connector/impl.go:89 (Connector.Register) ---\\n"
                + "Caused by: Failed to load S.H.I.E.L.D. config from /opt/shield/conf/shield.yaml\\n"
                + " --- at github.com/palantir/shield/connector/config.go:44 (withShieldConfig) ---\"";
        InputStream body = new ByteArrayInputStream(error.getBytes(StandardCharsets.UTF_8));
        Exception exception = GoErrorToExceptionConverter.getException(ImmutableSet.of(MediaType.APPLICATION_JSON),
                HttpStatus.BAD_REQUEST_400, "testReason", body);

        // verify outer exception
        assertThat(exception).isInstanceOf(GoException.class);
        assertThat(exception).hasMessage(
                "Error 400. Reason: testReason. Cause: Failed to register for villain discovery");
        assertThat(exception.getStackTrace()).isEqualTo(
                new StackTraceElement[] {
                        new StackTraceElement("ShieldAgent", "reallyRegister",
                                "github.com/palantir/shield/agent/discovery.go", 265),
                        new StackTraceElement("Connector", "Register", "github.com/palantir/shield/connector/impl.go",
                                89)
                });

        // verify first-level Go cause
        assertThat(exception.getCause()).isInstanceOf(GoException.class);
        assertThat(exception.getCause()).hasMessage(
                "Failed to load S.H.I.E.L.D. config from /opt/shield/conf/shield.yaml");
        assertThat(exception.getCause().getStackTrace()).isEqualTo(
                new StackTraceElement[] {new StackTraceElement("<no receiver>", "withShieldConfig",
                        "github.com/palantir/shield/connector/config.go", 44)});

        // verify second-level Java cause
        assertThat(exception.getCause().getCause()).isInstanceOf(RuntimeException.class);
        assertThat(exception.getCause().getCause().getMessage()).isNull();
    }

}
