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

package com.palantir.remoting.servers;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.StatusType;
import org.junit.Test;

public final class JsonExceptionMapperTest {

    private static class FooException extends RuntimeException {
        FooException(String message) {
            super(message);
        }
    }

    private static final Response.Status STATUS = Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE;

    private static class TestJsonExceptionMapper extends JsonExceptionMapper<FooException> {
        TestJsonExceptionMapper(boolean includeStackTrace) {
            super(includeStackTrace);
        }

        @Override
        protected StatusType getStatus(FooException exception) {
            return STATUS;
        }
    }

    private FooException createException(String message) {
        return new FooException(message);
    }

    private final TestJsonExceptionMapper mapper = new TestJsonExceptionMapper(true);
    private final TestJsonExceptionMapper noStacktraceMapper = new TestJsonExceptionMapper(false);

    @Test
    public void test_withStacktrace() {
        Response response = mapper.toResponse(createException("foo"));
        assertThat(response.getStatus(), is(STATUS.getStatusCode()));
        assertThat(response.getEntity().toString(), containsString("foo"));
        assertThat(response.getEntity().toString(),
                containsString("JsonExceptionMapperTest$FooException"));
        assertThat(response.getEntity().toString(), containsString("\"methodName\" : \"createException\""));
    }

    @Test
    public void test_withoutStacktrace() {
        Response response = noStacktraceMapper.toResponse(createException("foo"));
        assertThat(response.getStatus(), is(STATUS.getStatusCode()));
        assertThat(response.getEntity().toString(), not(containsString("foo")));
        assertThat(response.getEntity().toString(),
                containsString("JsonExceptionMapperTest$FooException"));
        assertThat(response.getEntity().toString(), not(containsString("\"methodName\" : \"createException\"")));
    }

    @Test
    public void test_noMessage() {
        Response response = new RuntimeExceptionMapper(true).toResponse(new NullPointerException());
        assertThat(response.getEntity().toString(), containsString("test_noMessage"));
    }
}
