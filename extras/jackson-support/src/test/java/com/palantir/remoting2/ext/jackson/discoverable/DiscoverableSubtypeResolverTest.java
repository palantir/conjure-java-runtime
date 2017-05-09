/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.ext.jackson.discoverable;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.util.Collections;
import org.junit.Test;

public class DiscoverableSubtypeResolverTest {
    private static final Class<Discoverable> ROOT_CLASS = Discoverable.class;

    @Test
    public void registerDiscoveredSubtypes() throws Exception {
        Class<?> clazz = Void.class;

        DiscoverableSubtypeResolver resolver =
                new DiscoverableSubtypeResolver(anyParent -> Collections.singleton(clazz), ROOT_CLASS);

        assertThat(resolver.registeredSubtypes()).containsExactly(new NamedType(clazz));
    }
}
