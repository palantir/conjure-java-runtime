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

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.OptionalInt;
import javax.inject.Inject;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NoContentException;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.message.MessageBodyWorkers;

@Provider
@Produces(MediaType.WILDCARD)
public final class Java8OptionalIntMessageBodyWriter implements MessageBodyWriter<OptionalInt> {

    @Inject
    private javax.inject.Provider<MessageBodyWorkers> mbw;

    // Jersey ignores this
    @Override
    public long getSize(
            OptionalInt entity, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return 0;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return OptionalInt.class.isAssignableFrom(type);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void writeTo(OptionalInt entity, Class<?> type, Type genericType, Annotation[] annotations,
            MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream)
            throws IOException {
        if (!entity.isPresent()) {
            throw new NoContentException("Absent value for type: " + genericType);
        }

        MessageBodyWriter writer = mbw.get().getMessageBodyWriter(Integer.class,
                Integer.class, annotations, mediaType);

        writer.writeTo(entity.getAsInt(), Integer.class,
                Integer.class, annotations, mediaType, httpHeaders, entityStream);
    }
}
