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

import static com.google.common.base.Preconditions.checkArgument;

import feign.Param.Expander;
import java.util.OptionalDouble;

/**
 * Expands OptionalDouble by using the empty string for {@link OptionalDouble#empty()} and the {@link Double#toString()}
 * of the value otherwise.
 */
public final class Java8EmptyOptionalDoubleExpander implements Expander {

    @Override
    public String expand(Object value) {
        checkArgument(value instanceof OptionalDouble, "Value must be an OptionalDouble. Was: %s", value.getClass());
        OptionalDouble optional = (OptionalDouble) value;
        return optional.isPresent() ? Double.toString(optional.getAsDouble()) : "";
    }
}
