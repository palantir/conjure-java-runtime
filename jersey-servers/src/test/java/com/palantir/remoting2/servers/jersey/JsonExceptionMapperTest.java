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

package com.palantir.remoting2.servers.jersey;

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
        @Override
        protected StatusType getStatus(FooException exception) {
            return STATUS;
        }
    }

    private FooException createException(String message) {
        return new FooException(message);
    }

    private final TestJsonExceptionMapper mapper = new TestJsonExceptionMapper();

    @Test
    public void testSanity() {
        Response response = mapper.toResponse(createException("foo"));
        assertThat(response.getStatus(), is(STATUS.getStatusCode()));
        assertThat(response.getEntity().toString(),
                containsString("JsonExceptionMapperTest$FooException"));
    }

    @Test
    public void testDoesNotPropagateExceptionMessage() {
        Response response = mapper.toResponse(createException("foo exception message"));
        assertThat(response.getEntity().toString(), not(containsString("foo exception message")));
    }

    @Test
    public void test_noMessage() {
        Response response = new RuntimeExceptionMapper().toResponse(new NullPointerException());
        assertThat(response.getEntity().toString(),
                containsString("\"errorCode\" : \"java.lang.NullPointerException\""));
        assertThat(response.getEntity().toString(),
                containsString("\"exceptionClass\" : \"java.lang.NullPointerException\""));
    }
}
