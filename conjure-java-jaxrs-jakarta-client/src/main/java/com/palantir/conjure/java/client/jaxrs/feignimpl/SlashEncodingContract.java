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

import feign.Contract;
import feign.MethodMetadata;
import java.lang.reflect.Method;

/** Decorates a {@link Contract} and forces slashes to be encoded when they are part of a URL. */
public final class SlashEncodingContract extends AbstractDelegatingContract {

    public SlashEncodingContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> _targetType, Method _method, MethodMetadata metadata) {
        metadata.template().decodeSlash(false);
    }
}
