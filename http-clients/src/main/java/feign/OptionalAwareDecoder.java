/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

package feign;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Decorates a Feign {@link Decoder} such that it returns {@link Optional#absent} when observing an HTTP 204 error code
 * for a method with {@link Type} {@link Optional}. This decoder only handles the case where the response object itself
 * is an {@link Optional}, and does not handle any occurrences of {@link Optional} objects that do not wrap the
 * top-level response. For example, if a method returns Map&lt;String, Optional&gt;, the inner Optional&lt;String&gt;
 * will not receive any special handling from this decoder (it will fall through to the delegated decoder).
 */
public final class OptionalAwareDecoder implements Decoder {

    private final Decoder delegate;

    public OptionalAwareDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (Types.getRawType(type).equals(Optional.class)) {
            if (response.status() == 204) {
                return Optional.absent();
            } else {
                Object decoded = checkNotNull(delegate.decode(response, getInnerType(type)),
                        "Unexpected null content for response status %d", response.status());
                return Optional.of(decoded);
            }
        } else {
            return delegate.decode(response, type);
        }
    }

    private static Type getInnerType(Type type) {
        ParameterizedType paramType = (ParameterizedType) type;
        return paramType.getActualTypeArguments()[0];
    }
}
