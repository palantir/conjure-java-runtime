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

package com.palantir.remoting2.servers.jersey;

import static com.google.common.base.Preconditions.checkArgument;

import com.palantir.tokens.auth.AuthHeader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;

@Provider
public final class AuthHeaderParamConverterProvider implements ParamConverterProvider {
    private final AuthHeaderParamConverter paramConverter = new AuthHeaderParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(final Class<T> rawType, final Type genericType,
                                              final Annotation[] annotations) {
        return AuthHeader.class.equals(rawType) ? (ParamConverter<T>) paramConverter : null;
    }

    public static final class AuthHeaderParamConverter implements ParamConverter<AuthHeader> {
        @Override
        public AuthHeader fromString(final String value) {
            try {
                return AuthHeader.valueOf(value);
            } catch (IllegalArgumentException e) {
                // rethrow as forbidden with 'Bearer' challenge
                throw new NotAuthorizedException(e, "Bearer");
            }
        }

        @Override
        public String toString(final AuthHeader value) {
            checkArgument(value != null);
            return value.toString();
        }
    }
}
