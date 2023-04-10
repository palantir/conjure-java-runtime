/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeBindings;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.util.List;
import org.junit.Test;

public class TypeBindingsTest {
    @Test
    public void testTypeBindings() {
        TypeBindings first = TypeBindings.create(
                List.<String>of("E"),
                List.<JavaType>of(TypeFactory.defaultInstance().constructType(String.class)));
        TypeBindings second = TypeBindings.create(
                List.<String>of("T"),
                List.<JavaType>of(TypeFactory.defaultInstance().constructType(String.class)));
        assertThat(first).isNotEqualTo(second);
        first.asKey(List.class);
    }
}
