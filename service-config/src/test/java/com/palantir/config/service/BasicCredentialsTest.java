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

package com.palantir.config.service;

import org.junit.Test;

public final class BasicCredentialsTest {

    @Test(expected = IllegalArgumentException.class)
    public void blankUsernameNotAllowed() {
        BasicCredentials.builder()
                .username("")
                .password("password")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankPasswordNotAllowed() {
        BasicCredentials.builder()
                .username("username")
                .password("")
                .build();
    }

    @Test
    public void testValid() {
        BasicCredentials.builder()
                .username("username")
                .password("password")
                .build();
    }
}
