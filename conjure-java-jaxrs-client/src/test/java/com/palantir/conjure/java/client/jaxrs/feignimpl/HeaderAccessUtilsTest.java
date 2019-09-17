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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.google.common.collect.ImmutableMap;
import java.util.Arrays;
import java.util.Collection;
import org.assertj.core.api.HamcrestCondition;
import org.junit.Test;

public final class HeaderAccessUtilsTest {
    private static final ImmutableMap<String, Collection<String>> TEST_HEADERS_MAP = ImmutableMap.of(
            "header",
            Arrays.asList("value1"),
            "Header",
            Arrays.asList("value2", "value3"),
            "HEADER",
            Arrays.asList("value4", "value5"));

    @Test
    public void caseInsensitiveContainsShouldReturnTrueIgnoringCase() {
        assertThat(HeaderAccessUtils.caseInsensitiveContains(TEST_HEADERS_MAP, "hEaDeR")).is(new HamcrestCondition<>(is(true)));
    }

    @Test
    public void caseInsensitiveContainsShouldReturnFalseForNonExistentKey() {
        assertThat(HeaderAccessUtils.caseInsensitiveContains(TEST_HEADERS_MAP, "invalid")).is(new HamcrestCondition<>(is(false)));
    }

    @Test
    public void caseInsensitiveGetReturnsNullForNotExistingHeader() {
        assertThat(HeaderAccessUtils.caseInsensitiveGet(TEST_HEADERS_MAP, "invalid")).is(new HamcrestCondition<>(is(nullValue())));
    }

    @Test
    public void caseInsensitiveGetReturnsAllExistingHeaders() {
        assertThat(HeaderAccessUtils.caseInsensitiveGet(TEST_HEADERS_MAP, "HeADER")).is(new HamcrestCondition<>(contains("value1", "value2", "value3", "value4", "value5")));
    }
}
