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

package com.palantir.remoting2.clients;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.VersionTest;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public final class UserAgentsTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private static final String EXPECTED_VERSION = "test-name (test-version)";

    @Test
    public void testGetUserAgent_format() {
        assertThat(UserAgents.getUserAgent("name", "version"), is("name (version)"));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testGetUserAgent_fromExternalVersionTestJar() throws IOException {
        assertThat(UserAgents.fromClass(VersionTest.class), is(EXPECTED_VERSION));
    }

    @Test
    public void testGetUserAgentLiberal_fromExternalVersionTestJar() {
        assertThat(UserAgents.fromClass(VersionTest.class, "fail", "fail"), is(EXPECTED_VERSION));
    }

    @Test
    public void testGetUserAgentLiberal_fromThisClass() {
        assertThat(UserAgents.fromClass(this.getClass(), "pass-name", "pass-version"), is("pass-name (pass-version)"));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetUserAgentStrict_fromExternalVersionTestJar() {
        assertThat(UserAgents.fromClassStrict(VersionTest.class), is(EXPECTED_VERSION));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testGetUserAgentStrict_fromThisClass() {
        String expectedMessage = "Implementation-Title missing from the manifest of class "
                + this.getClass().getCanonicalName();
        thrown.expectMessage(expectedMessage);
        thrown.expect(IllegalArgumentException.class);

        UserAgents.fromClassStrict(this.getClass());
    }
}
