/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import feign.MethodMetadata;
import feign.Param.Expander;
import java.util.HashMap;
import java.util.Map;

/** Utility functionality to safely add an expander instance rather than a class reference. */
final class Expanders {

    static void add(MethodMetadata metadata, int index, Expander instance) {
        Map<Integer, Expander> expanders = metadata.indexToExpander();
        if (expanders == null) {
            Map<Integer, Class<? extends Expander>> expanderClasses = metadata.indexToExpanderClass();
            if (!expanderClasses.isEmpty()) {
                throw new SafeIllegalStateException(
                        "An expander class has unexpectedly been registered",
                        SafeArg.of("unexpected", expanderClasses));
            }
            expanders = new HashMap<>();
            metadata.indexToExpander(expanders);
        }
        expanders.put(index, instance);
    }

    private Expanders() {}
}
