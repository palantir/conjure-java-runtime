/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.conjure.java.client.jaxrs;

import java.util.List;

/**
 * Defines what annotations and values are valid on interfaces.
 */
@SuppressWarnings("MissingSummary")
interface Contract {

    /**
     * Called to parse the methods in the class that are linked to HTTP requests.
     *
     * @param targetType {@link Target#type() type} of the Feign interface.
     */
    List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType);
}
