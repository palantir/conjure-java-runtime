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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.jaxrs.TestBase;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class GuavaOptionalAwareDecoderTest extends TestBase {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(GuavaTestServer.class,
            "src/test/resources/test-server.yml");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private GuavaTestServer.TestService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(
                GuavaTestServer.TestService.class, AGENT, new HostMetricsRegistry(), createTestConfig(endpointUri));
    }

    @Test
    public void testOptional() {
        assertThat(service.getOptional("something"), is(Optional.of(ImmutableMap.of("something", "something"))));
        assertThat(service.getOptional(null), is(Optional.<ImmutableMap<String, String>>absent()));
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
    public void testThrowsNotAuthorized() {
        try {
            service.getThrowsNotAuthorized(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("RemoteException: javax.ws.rs.NotAuthorizedException"));
            assertThat(e.getError().errorCode(), is("javax.ws.rs.NotAuthorizedException"));
        }
    }

    @Test
    public void testOptionalThrowsNotAuthorized() {
        try {
            service.getOptionalThrowsNotAuthorized(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("RemoteException: javax.ws.rs.NotAuthorizedException"));
            assertThat(e.getError().errorCode(), is("javax.ws.rs.NotAuthorizedException"));
        }
    }

    @Test
    public void testThrowsFordidden() {
        try {
            service.getThrowsForbidden(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("RemoteException: PERMISSION_DENIED (Default:PermissionDenied)"));
            assertThat(e.getError().errorCode(), is("PERMISSION_DENIED"));
        }
    }

    @Test
    public void testOptionalThrowsForbbbden() {
        try {
            service.getOptionalThrowsForbidden(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("RemoteException: PERMISSION_DENIED (Default:PermissionDenied)"));
            assertThat(e.getError().errorCode(), is("PERMISSION_DENIED"));
        }
    }

    @Test
    public void testOptionalString() {
        assertThat(service.getOptionalString(null), is(Optional.absent()));
        assertThat(service.getOptionalString("foo"), is(Optional.of("foo")));
    }

    @Test
    public void testComplexType() {
        GuavaOptionalComplexType value = new GuavaOptionalComplexType(
                Optional.of(
                        new GuavaOptionalComplexType(
                                Optional.absent(),
                                Optional.absent(),
                                Paths.get("bar"))),
                Optional.of("baz"),
                Paths.get("foo"));
        // Hint: set breakpoint in Feign's SynchronousMethodHandler#executeAndDecode to inspect serialized parameter.
        assertThat(service.getGuavaComplexType(value), is(value));
    }

    @Test
    public void testCborResponse() {
        GuavaOptionalComplexType value = new GuavaOptionalComplexType(
                Optional.of(
                        new GuavaOptionalComplexType(
                                Optional.<GuavaOptionalComplexType>absent(),
                                Optional.<String>absent(),
                                Paths.get("bar"))),
                Optional.of("baz"),
                Paths.get("foo"));
        assertThat(service.getCborResponse(value), is(value));
    }

    @Test
    public void testCborRequest() {
        GuavaOptionalComplexType value = new GuavaOptionalComplexType(
                Optional.of(
                        new GuavaOptionalComplexType(
                                Optional.<GuavaOptionalComplexType>absent(),
                                Optional.<String>absent(),
                                Paths.get("bar"))),
                Optional.of("baz"),
                Paths.get("foo"));
        assertThat(service.postCborRequest(value), is(value));
    }
}
