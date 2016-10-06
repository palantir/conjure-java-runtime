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

package com.palantir.remoting1.jaxrs.feignimpl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.palantir.remoting1.errors.RemoteException;
import com.palantir.remoting1.jaxrs.JaxRsClient;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class OptionalAwareDecoderTest {

    @ClassRule
    public static final DropwizardAppRule<TestConfiguration> APP = new DropwizardAppRule<>(TestServer.class,
            "src/test/resources/test-server.yml");

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private TestServer.TestService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.builder()
                .build(TestServer.TestService.class, "agent", endpointUri);
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
            assertThat(e.getMessage(), containsString("Not found"));
            assertThat(e.getRemoteException().getErrorName(), is("javax.ws.rs.NotFoundException"));
        }
    }

    @Test
    public void testThrowsNotAuthorized() {
        try {
            service.getThrowsNotAuthorized(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("Unauthorized"));
            assertThat(e.getRemoteException().getErrorName(), is("javax.ws.rs.NotAuthorizedException"));
        }
    }

    @Test
    public void testOptionalThrowsNotAuthorized() {
        try {
            service.getOptionalThrowsNotAuthorized(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("Unauthorized"));
            assertThat(e.getRemoteException().getErrorName(), is("javax.ws.rs.NotAuthorizedException"));
        }
    }

    @Test
    public void testThrowsFordidden() {
        try {
            service.getThrowsForbidden(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("Forbidden"));
            assertThat(e.getRemoteException().getErrorName(), is("javax.ws.rs.ForbiddenException"));
        }
    }

    @Test
    public void testOptionalThrowsFordidden() {
        try {
            service.getOptionalThrowsForbidden(null);
            fail();
        } catch (RemoteException e) {
            assertThat(e.getMessage(), containsString("Forbidden"));
            assertThat(e.getRemoteException().getErrorName(), is("javax.ws.rs.ForbiddenException"));
        }
    }

    @Test
    public void testComplexType() {
        ComplexType value = new ComplexType(
                Optional.of(
                        new ComplexType(
                                Optional.<ComplexType>absent(),
                                Optional.<String>absent(),
                                Paths.get("bar"))),
                Optional.of("baz"),
                Paths.get("foo"));
        // Hint: set breakpoint in Feign's SynchronousMethodHandler#executeAndDecode to inspect serialized parameter.
        assertThat(service.getComplexType(value), is(value));
    }
}
