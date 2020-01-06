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
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.internal.inject.Custom;

// The Custom annotation ensures that our custom param converters are considered first. See ParamConverterFactory.
// This is particularly important for the AuthHeader param converter to ensure that we do not fallback to the
// TypeValueOf param converter.
@Custom
@Provider
public final class AuthHeaderParamConverterProvider implements ParamConverterProvider {
    private final AuthHeaderParamConverter paramConverter = new AuthHeaderParamConverter();

    @Override
    @SuppressWarnings("unchecked")
    public <T> ParamConverter<T> getConverter(
            final Class<T> rawType,
            final Type _genericType,
            final Annotation[] annotations) {
        return AuthHeader.class.equals(rawType) && hasAuthAnnotation(annotations)
                ? (ParamConverter<T>) paramConverter
                : null;
    }

    public static final class AuthHeaderParamConverter implements ParamConverter<AuthHeader> {
        @Override
        public AuthHeader fromString(final String value) {
            if (value == null) {
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
}
