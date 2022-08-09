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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import static com.google.common.base.Preconditions.checkNotNull;

import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Decorates a Feign {@link Decoder} such that it returns {@link com.google.common.base.Optional#absent} when observing
 * an HTTP 204 error code for a method with {@link Type} {@link com.google.common.base.Optional}.
 */
public final class GuavaOptionalAwareDecoder implements Decoder {

    private final Decoder delegate;

    public GuavaOptionalAwareDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (RawTypes.get(type).equals(com.google.common.base.Optional.class)) {
            if (response.status() == 204) {
                return com.google.common.base.Optional.absent();
            } else {
                Object decoded = checkNotNull(
                        delegate.decode(response, getInnerType(type)),
                        "Unexpected null content for response status %s",
                        response.status());
                return com.google.common.base.Optional.of(decoded);
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
