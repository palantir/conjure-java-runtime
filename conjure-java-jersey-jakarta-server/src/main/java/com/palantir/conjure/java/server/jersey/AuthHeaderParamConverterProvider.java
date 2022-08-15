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
import com.palantir.tokens.auth.AuthHeader;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.annotation.Nullable;
import org.glassfish.jersey.internal.inject.Custom;

// The Custom annotation ensures that our custom param converters are considered first. See ParamConverterFactory.
// This is particularly important for the AuthHeader param converter to ensure that we do not fallback to the
// TypeValueOf param converter.
@Custom
@Provider
public final class AuthHeaderParamConverterProvider implements ParamConverterProvider {
    private static final AuthHeaderParamConverter nonNullParamConverter = new AuthHeaderParamConverter(false);
    private static final AuthHeaderParamConverter nullableParamConverter = new AuthHeaderParamConverter(true);

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(
            final Class<T> rawType, final Type _genericType, final Annotation[] annotations) {
        if (AuthHeader.class.equals(rawType) && hasAuthAnnotation(annotations)) {
            return (ParamConverter<T>)
                    (hasNullableAnnotation(annotations) ? nullableParamConverter : nonNullParamConverter);
        }
        return null;
    }

    public static final class AuthHeaderParamConverter implements ParamConverter<AuthHeader> {

        private final boolean nullable;

        /**
         * This class should not be used directly.
         *
         * @deprecated Use AuthHeaderParamConverterProvider
         */
        @Deprecated
        public AuthHeaderParamConverter() {
            this(false);
        }

        private AuthHeaderParamConverter(boolean nullable) {
            this.nullable = nullable;
        }

        @Override
        public AuthHeader fromString(final String value) {
            if (value == null) {
                if (nullable) {
                    return null;
                }
                throw UnauthorizedException.missingCredentials();
            }
            try {
                return AuthHeader.valueOf(value);
            } catch (RuntimeException e) {
                throw UnauthorizedException.malformedCredentials(e);
            }
        }

        @Override
        public String toString(final AuthHeader value) {
            Preconditions.checkArgument(value != null);
            return value.toString();
        }
    }

    private static boolean hasAuthAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (HeaderParam.class.equals(annotation.annotationType())) {
                String value = ((HeaderParam) annotation).value();
                if (HttpHeaders.AUTHORIZATION.equals(value)) {
                    return true;
                }
            }
        }

        return false;
    }

    private static boolean hasNullableAnnotation(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (Nullable.class.equals(annotation.annotationType())) {
                return true;
            }
        }
        return false;
    }
}
