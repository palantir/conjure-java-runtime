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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import org.junit.Test;

public final class WebApplicationExceptionMapperTest {

    private static final Response.Status STATUS = Response.Status.EXPECTATION_FAILED;

    private final WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper(true);
    private final WebApplicationExceptionMapper noStacktraceMapper = new WebApplicationExceptionMapper(false);

    @Test
    public void test_withStacktrace() {
        Response response = mapper.toResponse(new WebApplicationException(STATUS));
        assertThat(response.getStatus(), is(STATUS.getStatusCode()));
        assertThat(response.getEntity().toString(), containsString(STATUS.getReasonPhrase()));
        assertThat(response.getEntity().toString(),
                containsString("javax.ws.rs.WebApplicationException"));
        assertThat(response.getEntity().toString(), containsString("\"methodName\" : \"test"));
    }

    @Test
    public void test_withoutStacktrace() {
        Response response = noStacktraceMapper.toResponse(new WebApplicationException(STATUS));
        assertThat(response.getStatus(), is(STATUS.getStatusCode()));
        assertThat(response.getEntity().toString(), not(containsString(STATUS.getReasonPhrase())));
        assertThat(response.getEntity().toString(),
                containsString("javax.ws.rs.WebApplicationException"));
        assertThat(response.getEntity().toString(), not(containsString("\"methodName\" : \"test")));
    }
}
