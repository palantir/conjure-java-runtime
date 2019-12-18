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

package com.palantir.conjure.java.server.jersey;

import com.palantir.logsafe.Preconditions;
import com.palantir.tokens.auth.BearerToken;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.CookieParam;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.internal.inject.Custom;

@Custom
@Provider
public final class BearerTokenParamConverterProvider implements ParamConverterProvider {
    private final BearerTokenParamConverter paramConverter = new BearerTokenParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(
            final Class<T> rawType,
            final Type _genericType,
            final Annotation[] annotations) {
        return BearerToken.class.equals(rawType) && hasAuthAnnotation(annotations)
                ? (ParamConverter<T>) paramConverter
                : null;
    }

    public static final class BearerTokenParamConverter implements ParamConverter<BearerToken> {
        @Override
        public BearerToken fromString(final String value) {
            if (value == null) {
                throw UnauthorizedException.missingCredentials();
            }
            try {
                return BearerToken.valueOf(value);
            } catch (RuntimeException e) {
                throw UnauthorizedException.malformedCredentials(e);
            }
        }

        @Override
        public String toString(final BearerToken value) {
            Preconditions.checkArgument(value != null);
            return value.toString();
        }
    }

    private static boolean hasAuthAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType() == CookieParam.class) {
                return true;
            }
        }

        return false;
    }
}
