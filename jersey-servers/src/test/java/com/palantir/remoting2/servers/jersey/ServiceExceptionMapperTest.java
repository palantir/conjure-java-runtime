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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.common.collect.Lists;
import com.palantir.remoting2.errors.Param;
import com.palantir.remoting2.errors.SafeParam;
import com.palantir.remoting2.errors.ServiceException;
import com.palantir.remoting2.errors.UnsafeParam;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import java.util.List;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.junit.Test;

public final class ServiceExceptionMapperTest {

    private static final int STATUS = 499;

    private ServiceException exception = new ServiceException(STATUS, "foo");
    private final ServiceExceptionMapper mapper = new ServiceExceptionMapper();

    @Test
    public void testResponse() {
        Response response = mapper.toResponse(exception);
        assertThat(response.getStatus(), is(STATUS));
        assertThat(response.getMediaType().toString(), is(MediaType.APPLICATION_JSON));
        assertThat(response.getEntity().toString(), is(serialize(exception.getError())));
    }

    @Test
    public void testLogMessage() {
        String messageTemplate = "arg1={}, arg2={}";
        Param<?>[] args = {
                SafeParam.of("arg1", "foo"),
                UnsafeParam.of("arg2", "bar")};

        assertLogMessageIsCorrect(messageTemplate, args);
    }

    @Test
    public void testLogMessageWithNoParams() {
        assertLogMessageIsCorrect("error");
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

    private void assertLogMessageIsCorrect(String messageFormat, Param... params) {
        ServiceException ex = new ServiceException(messageFormat, params);

        String expectedMessageFormat = "Error handling request {}: " + messageFormat;

        List<Object> expectedParams = Lists.newArrayList();
        expectedParams.add(SafeParam.of("errorId", ex.getErrorId()));
        expectedParams.addAll(Lists.newArrayList(params));
        expectedParams.add(ex);

        assertThat(mapper.getLogMessageFormat(ex), is(expectedMessageFormat));
        assertThat(mapper.getLogMessageParams(ex), is(expectedParams.toArray()));
    }
}
