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

package feign;

import static com.google.common.base.Preconditions.checkNotNull;

import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Function;

/**
 * Decorates a Feign {@link Decoder} such that it returns {@link Optional#empty} when observing an HTTP 204 error code
 * for a method with {@link Type} {@link Optional}.
 */
public final class Java8OptionalAwareDecoder implements Decoder {
    private static final int NO_CONTENT = 204;

    private final Decoder delegate;

    public Java8OptionalAwareDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        Class<?> rawType = Types.getRawType(type);
        if (rawType.equals(Optional.class)) {
            return decodeOptional(response, type);
        }

        if (rawType.equals(OptionalInt.class)) {
            return decodeOptionalInt(response);
        }

        return delegate.decode(response, type);
    }

    private Object decodeOptional(Response response, Type type) throws IOException {
        return decodeOptionalFromContent(response, getInnerType(type),
                Optional.empty(),
                Optional::of
        );
    }

    private Object decodeOptionalInt(Response response) throws IOException {
        return decodeOptionalFromContent(response, String.class,
                OptionalInt.empty(),
                (decoded) -> OptionalInt.of(Integer.parseInt((String) decoded))
        );
    }

    private <T> T decodeOptionalFromContent(
            Response response,
            Type type,
            T empty,
            Function<Object, T> notEmpty) throws IOException {

        if (response.status() == NO_CONTENT) {
            return empty;
        }

        Object decoded = checkNotNull(delegate.decode(response, type),
                "Unexpected null content for response status %d", response.status());
        return notEmpty.apply(decoded);
    }

    private static Type getInnerType(Type type) {
        ParameterizedType paramType = (ParameterizedType) type;
        return paramType.getActualTypeArguments()[0];
    }
}
