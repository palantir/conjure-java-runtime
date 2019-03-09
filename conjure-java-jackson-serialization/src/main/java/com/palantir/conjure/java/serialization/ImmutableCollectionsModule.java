/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Values deserialized into {@link List}, {@link Set}, and {@link Map}, use Guava implementations of
 * {@link ImmutableList}, {@link ImmutableSet}, and {@link ImmutableMap} respectively.
 */
final class ImmutableCollectionsModule extends SimpleModule {
    private static final long serialVersionUID = 1L;

    ImmutableCollectionsModule() {
        super(ImmutableCollectionsModule.class.getCanonicalName());
        addAbstractTypeMapping(List.class, ImmutableList.class);
        addAbstractTypeMapping(Map.class, ImmutableMap.class);
        addAbstractTypeMapping(Set.class, ImmutableSet.class);
    }
}
