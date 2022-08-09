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

import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.NoContentException;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.OptionalDouble;

/*
 * Adapted from https://github.com/dropwizard/dropwizard/blob/master/dropwizard-jersey/src/main/java/io/dropwizard/jersey/optional/OptionalDoubleMessageBodyWriter.java
 */
@Provider
@Produces(MediaType.WILDCARD)
public final class Java8OptionalDoubleMessageBodyWriter implements MessageBodyWriter<OptionalDouble> {

    // Jersey ignores this
    @Override
    public long getSize(
            OptionalDouble _entity,
            Class<?> _type,
            Type _genericType,
            Annotation[] _annotations,
            MediaType _mediaType) {
        return 0;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type _genericType, Annotation[] _annotations, MediaType _mediaType) {
        return OptionalDouble.class.isAssignableFrom(type);
    }

    @Override
    public void writeTo(
            OptionalDouble entity,
            Class<?> _type,
            Type genericType,
            Annotation[] _annotations,
            MediaType _mediaType,
            MultivaluedMap<String, Object> _httpHeaders,
            OutputStream entityStream)
            throws IOException {
        if (!entity.isPresent()) {
            throw new NoContentException("Absent value for type: " + genericType);
        }

        entityStream.write(Double.toString(entity.getAsDouble()).getBytes(StandardCharsets.US_ASCII));
    }
}
