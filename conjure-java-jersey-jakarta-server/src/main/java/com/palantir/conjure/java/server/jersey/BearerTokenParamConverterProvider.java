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
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import org.glassfish.jersey.internal.inject.Custom;

// The Custom annotation ensures that our custom param converters are considered first. See ParamConverterFactory.
// This is particularly important for the BearerToken param converter to ensure that we do not fallback to the
// TypeValueOf param converter.
@Custom
@Provider
public final class BearerTokenParamConverterProvider implements ParamConverterProvider {
    private static final BearerTokenParamConverter nonNullParamConverter = new BearerTokenParamConverter(false);
    private static final BearerTokenParamConverter nullableParamConverter = new BearerTokenParamConverter(true);

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(
            final Class<T> rawType, final Type _genericType, final Annotation[] annotations) {
        if (BearerToken.class.equals(rawType) && hasAnnotation(annotations, CookieParam.class)) {
            return (ParamConverter<T>)
                    (hasAnnotation(annotations, Nullable.class) ? nullableParamConverter : nonNullParamConverter);
        }
        return null;
    }

    public static final class BearerTokenParamConverter implements ParamConverter<BearerToken> {

        private final boolean nullable;

        /**
         * This class should not be used directly.
         *
         * @deprecated Use BearerTokenParamConverterProvider
         */
        @Deprecated
        public BearerTokenParamConverter() {
            this(false);
        }

        private BearerTokenParamConverter(boolean nullable) {
            this.nullable = nullable;
        }

        @Override
        public BearerToken fromString(final String value) {
            if (value == null) {
                if (nullable) {
                    return null;
                }
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

    private static boolean hasAnnotation(Annotation[] annotations, Class<? extends Annotation> target) {
        for (Annotation annotation : annotations) {
            if (target.equals(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }
}
