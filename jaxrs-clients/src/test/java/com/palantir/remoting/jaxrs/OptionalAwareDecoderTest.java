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

package com.palantir.remoting.jaxrs;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class OptionalAwareDecoderTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(TestServer.class,
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
        expectedException.expect(NotFoundException.class);
        expectedException.expectMessage(containsString("Not found"));
        service.getThrowsNotFound(null);
    }

    @Test
    public void testThrowsNotAuthorized() {
        // Throws RuntimeException since no exception mapper is registered.
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("Unauthorized"));
        service.getThrowsNotAuthorized(null);
    }

    @Test
    public void testOptionalThrowsNotAuthorized() {
        // Throws RuntimeException since no exception mapper is registered.
        expectedException.expect(RuntimeException.class);
        expectedException.expectMessage(containsString("Unauthorized"));
        service.getOptionalThrowsNotAuthorized(null);
    }

    @Test
    public void testThrowsFordidden() {
        expectedException.expect(ForbiddenException.class);
        expectedException.expectMessage(containsString("Forbidden"));
        service.getThrowsForbidden(null);
    }

    @Test
    public void testOptionalThrowsFordidden() {
        expectedException.expect(ForbiddenException.class);
        expectedException.expectMessage(containsString("Forbidden"));
        service.getOptionalThrowsForbidden(null);
    }
}
