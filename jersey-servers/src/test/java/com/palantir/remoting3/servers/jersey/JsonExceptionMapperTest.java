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

package com.palantir.remoting3.servers.jersey;

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.remoting.api.errors.ErrorType;
import javax.ws.rs.core.Response;
import org.junit.Test;

public final class JsonExceptionMapperTest {

    private final JsonExceptionMapper<RuntimeException> mapper = new JsonExceptionMapper<RuntimeException>() {
        @Override
        ErrorType getErrorType(RuntimeException exception) {
            return ErrorType.INVALID_ARGUMENT;
        }
    };

    @Test
    public void testExpectedSerializedError() {
        Response response = mapper.toResponse(new NullPointerException("foo"));
        assertThat(response.getEntity().toString()).contains("\"errorCode\" : \"INVALID_ARGUMENT\"");
        assertThat(response.getEntity().toString()).contains("\"errorName\" : \"Default:InvalidArgument\"");
        assertThat(response.getEntity().toString()).contains("\"exceptionClass\" : \"java.lang.NullPointerException\"");
        assertThat(response.getEntity().toString())
                .contains("\"message\" : \"Refer to the server logs with this errorInstanceId:");
    }

    @Test
    public void testDoesNotPropagateExceptionMessage() {
        Response response = new RuntimeExceptionMapper().toResponse(new NullPointerException("secret"));
        assertThat(response.getEntity().toString()).doesNotContain("secret");
    }
}
