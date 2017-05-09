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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.palantir.remoting2.errors.SerializableError;
import com.palantir.remoting2.errors.ServiceException;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;

public final class ServiceExceptionMapperTest {

    private static final SerializableError ERROR = SerializableError.of("foo", "bar");
    private static final String SERIALIZED_ERROR = serialize(ERROR);

    private static final int STATUS = 499;

    private final ServiceException exception = mock(ServiceException.class);
    private final ServiceExceptionMapper mapper = new ServiceExceptionMapper();

    @Before
    public void before() {
        when(exception.getError()).thenReturn(ERROR);
        when(exception.getStatus()).thenReturn(STATUS);
    }

    @Test
    public void testResponse() {
        Response response = mapper.toResponse(exception);
        assertThat(response.getStatus(), is(STATUS));
        assertThat(response.getMediaType().toString(), is(MediaType.APPLICATION_JSON));
        assertThat(response.getEntity().toString(), is(SERIALIZED_ERROR));
    }

    @Test
    public void testInvokesLog() {
        mapper.toResponse(exception);
        verify(exception, times(1)).logTo(any());
    }

    private static String serialize(Object obj) {
        ObjectMapper mapper = ObjectMappers.newClientObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        try {
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
