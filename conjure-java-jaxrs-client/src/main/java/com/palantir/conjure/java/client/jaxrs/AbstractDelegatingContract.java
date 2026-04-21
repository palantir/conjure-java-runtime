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
 * Base class that provides the structure for a delegating {@link Contract}. Delegates the initial
 * {@link #parseAndValidateMetadata(Class)} call to the wrapped Contract and then calls {@link #processMetadata(Class,
 * MethodMetadata)} on all of the methods that have metadata from the initial call.
 */
abstract class AbstractDelegatingContract implements Contract {

    private final Contract delegate;

    AbstractDelegatingContract(Contract delegate) {
        this.delegate = delegate;
    }

    @Override
    public final List<MethodMetadata> parseAndValidateMetadata(Class<?> targetType) {
        List<MethodMetadata> methodMetadatas = delegate.parseAndValidateMetadata(targetType);

        methodMetadatas.forEach(methodMetadata -> {
            processMetadata(targetType, methodMetadata);
        });

        return methodMetadatas;
    }

    abstract void processMetadata(Class<?> targetType, MethodMetadata metadata);
}
