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

package com.palantir.remoting.clients;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.palantir.VersionTest;
import java.io.IOException;
import org.junit.Test;

public final class UserAgentsTest {

    @Test
    public void testGetUserAgent_format() {
        assertThat(UserAgents.getUserAgent("name", "version"), is("name (version)"));
    }

    @Test
    public void testGetUserAgent_fromExternalVersionTestJar() throws IOException {
        assertThat(UserAgents.fromClass(VersionTest.class), is("test-name (test-version)"));
    }
}
