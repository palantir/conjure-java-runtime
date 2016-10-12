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

package com.palantir.remoting1.servers.jersey;

import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.Response.StatusType;
import javax.ws.rs.ext.Provider;

@Provider
final class RuntimeExceptionMapper extends JsonExceptionMapper<RuntimeException> {

    RuntimeExceptionMapper(boolean includeStackTrace) {
        super(includeStackTrace);
    }

    @Override
    protected StatusType getStatus(RuntimeException exception) {
        return Status.INTERNAL_SERVER_ERROR;
    }
}
