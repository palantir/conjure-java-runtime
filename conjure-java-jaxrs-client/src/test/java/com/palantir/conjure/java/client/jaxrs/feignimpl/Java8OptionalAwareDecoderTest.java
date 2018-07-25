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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.client.jaxrs.feignimpl.Java8TestServer.TestService;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import java.util.Optional;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class Java8OptionalAwareDecoderTest extends TestBase {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(Java8TestServer.class,
            "src/test/resources/test-server.yml");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Java8TestServer.TestService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(TestService.class,
                AGENT,
                new HostMetricsRegistry(),
                createTestConfig(endpointUri));
    }

    @Test
    public void testOptional() {
        assertThat(service.getOptional("something"), is(Optional.of(ImmutableMap.of("something", "something"))));
        assertThat(service.getOptional(null), is(Optional.<ImmutableMap<String, String>>empty()));
    }

    @Test
    public void testNonOptional() {
        assertThat(service.getNonOptional("something"), is(ImmutableMap.of("something", "something")));
        assertThat(service.getNonOptional(null), is(ImmutableMap.<String, String>of()));
    }

    @Test
    public void testThrowsNotFound() {
        try {
            service.getThrowsNotFound(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("RemoteException: NOT_FOUND (Default:NotFound)"));
            assertThat(e.getError().errorCode(), is("NOT_FOUND"));
        }
    }

    @Test
    public void testThrowsPermissionDenied() {
        try {
            service.getThrowsPermissionDenied(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("RemoteException: PERMISSION_DENIED (Default:PermissionDenied)"));
            assertThat(e.getError().errorCode(), is("PERMISSION_DENIED"));
        }
    }

    @Test
    public void testOptionalThrowsPermissionDenied() {
        try {
            service.getOptionalThrowsPermissionDenied(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("RemoteException: PERMISSION_DENIED (Default:PermissionDenied)"));
            assertThat(e.getError().errorCode(), is("PERMISSION_DENIED"));
        }
    }

    @Test
    public void testOptionalString() {
        assertThat(service.getOptionalString(null), is(Optional.empty()));
        assertThat(service.getOptionalString("foo"), is(Optional.of("foo")));
    }

    @Test
    public void testComplexType() {
        Java8ComplexType value = new Java8ComplexType(
                Optional.of(
                        new Java8ComplexType(
                                Optional.empty(),
                                Optional.empty(),
                                Paths.get("bar"))),
                Optional.of("baz"),
                Paths.get("foo"));
        // Hint: set breakpoint in Feign's SynchronousMethodHandler#executeAndDecode to inspect serialized parameter.
        assertThat(service.getJava8ComplexType(value), is(value));
    }
}
