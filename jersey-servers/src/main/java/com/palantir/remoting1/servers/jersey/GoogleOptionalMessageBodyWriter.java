/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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
 *
 * Copied from Dropwizard on 2016-10-13. Source: https://github.com/dropwizard/dropwizard/blob/844b71047f6d70a2b507af416d7f1ad18d60b0dc/dropwizard-jersey/src/main/java/io/dropwizard/jersey/guava/OptionalMessageBodyWriter.java
 */

package com.palantir.remoting1.servers.jersey;

import com.google.common.base.Optional;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import javax.inject.Inject;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NoContentException;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.message.MessageBodyWorkers;

@Provider
@Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
public final class OptionalMessageBodyWriter implements MessageBodyWriter<Optional<?>> {

    @Inject
    private javax.inject.Provider<MessageBodyWorkers> mbw;

    // Jersey ignores this
    @Override
    public long getSize(
            Optional<?> entity, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return 0;
    }

    @Override
    public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
        return Optional.class.isAssignableFrom(type);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void writeTo(
            Optional<?> entity,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream)
            throws IOException, WebApplicationException {
        if (!entity.isPresent()) {
            throw new NoContentException("Absent value for type: " + genericType);
        }

        ParameterizedType actualGenericType = (ParameterizedType) genericType;
        MessageBodyWriter writer = mbw.get().getMessageBodyWriter(entity.get().getClass(),
                actualGenericType.getActualTypeArguments()[0], annotations, mediaType);
        writer.writeTo(entity.get(), entity.get().getClass(),
                actualGenericType.getActualTypeArguments()[0],
                annotations, mediaType, httpHeaders, entityStream);
    }
}
