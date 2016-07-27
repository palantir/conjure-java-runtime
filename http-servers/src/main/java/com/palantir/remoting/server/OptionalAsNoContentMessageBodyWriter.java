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
 */

package com.palantir.remoting.server;

import com.google.common.base.Optional;
import io.dropwizard.jersey.guava.OptionalMessageBodyWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.NoContentException;

/**
 * Overwrites the default dropwizard behavior of throwing {@code NotFoundException} for {@code Optional#absent()} and
 * instead throws {@link NoContentException}.
 */
@Produces(MediaType.APPLICATION_JSON)
public final class OptionalAsNoContentMessageBodyWriter extends OptionalMessageBodyWriter {

    @Override
    public void writeTo(Optional<?> entity,
            Class<?> type,
            Type genericType,
            Annotation[] annotations,
            MediaType mediaType,
            MultivaluedMap<String, Object> httpHeaders,
            OutputStream entityStream) throws IOException, WebApplicationException {
        if (!entity.isPresent()) {
            throw new NoContentException("Absent value for type: " + genericType);
        }
        super.writeTo(entity, type, genericType, annotations, mediaType, httpHeaders, entityStream);
    }
}
