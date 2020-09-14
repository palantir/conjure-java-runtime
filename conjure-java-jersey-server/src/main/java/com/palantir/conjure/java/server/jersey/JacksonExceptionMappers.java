/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.palantir.conjure.java.api.errors.ErrorType;
import javax.annotation.Priority;
import javax.ws.rs.core.FeatureContext;
import javax.ws.rs.ext.Provider;

final class JacksonExceptionMappers {

    private static final int HIGH_PRIORITY = 500;

    static void configure(FeatureContext context, ConjureJerseyFeature.ExceptionListener exceptionListener) {
        context.register(exceptionListener.augment(new InvalidDefinitionExceptionMapper()));
        context.register(exceptionListener.augment(new JsonGenerationExceptionMapper()));
        context.register(exceptionListener.augment(new JsonMappingExceptionMapper()));
        context.register(exceptionListener.augment(new JsonProcessingExceptionMapper()));
        context.register(exceptionListener.augment(new JsonParseExceptionMapper()));
    }

    @Provider
    @Priority(HIGH_PRIORITY) // Must be prioritized over com.fasterxml.jackson.jaxrs.base.JsonMappingExceptionMapper
    static final class JsonMappingExceptionMapper extends JsonExceptionMapper<JsonMappingException> {

        @Override
        ErrorType getErrorType(JsonMappingException _exception) {
            return ErrorType.INVALID_ARGUMENT;
        }
    }

    @Provider
    @Priority(HIGH_PRIORITY) // Higher priority to avoid interaction with potential future builtin mappers
    static final class InvalidDefinitionExceptionMapper extends JsonExceptionMapper<InvalidDefinitionException> {

        @Override
        ErrorType getErrorType(InvalidDefinitionException _exception) {
            return ErrorType.INTERNAL;
        }
    }

    @Provider
    @Priority(HIGH_PRIORITY) // Higher priority to avoid interaction with potential future builtin mappers
    static final class JsonGenerationExceptionMapper extends JsonExceptionMapper<JsonGenerationException> {

        @Override
        ErrorType getErrorType(JsonGenerationException _exception) {
            return ErrorType.INTERNAL;
        }
    }

    @Provider
    @Priority(HIGH_PRIORITY) // Higher priority to avoid interaction with potential future builtin mappers
    static final class JsonProcessingExceptionMapper extends JsonExceptionMapper<JsonProcessingException> {
        @Override
        ErrorType getErrorType(JsonProcessingException _exception) {
            return ErrorType.INVALID_ARGUMENT;
        }
    }

    @Provider
    @Priority(HIGH_PRIORITY) // Must be prioritized over com.fasterxml.jackson.jaxrs.base.JsonParseExceptionMapper
    static final class JsonParseExceptionMapper extends JsonExceptionMapper<JsonParseException> {

        @Override
        ErrorType getErrorType(JsonParseException _exception) {
            return ErrorType.INVALID_ARGUMENT;
        }
    }

    private JacksonExceptionMappers() {}
}
