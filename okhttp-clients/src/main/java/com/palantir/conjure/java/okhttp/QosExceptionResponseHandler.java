/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.okhttp;

import com.palantir.conjure.java.QosExceptionResponseMapper;
import com.palantir.conjure.java.api.errors.QosException;
import java.util.Optional;
import okhttp3.Response;

/** A {@link ResponseHandler} that turns QOS-related HTTP responses into {@link QosException}s. */
enum QosExceptionResponseHandler implements ResponseHandler<QosException> {
    INSTANCE;

    @Override
    public Optional<QosException> handle(Response response) {
        return QosExceptionResponseMapper.mapResponseCode(response.code(), response::header);
    }
}
