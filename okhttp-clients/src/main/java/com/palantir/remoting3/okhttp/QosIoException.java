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

package com.palantir.remoting3.okhttp;

import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import okhttp3.Response;

/**
 * An exception pertaining to http-remoting QoS situations, for example requests to re-schedule a remote request for a
 * later time, to try another server, etc. This class is mostly an {@link IOException}-wrapper for http-remoting {@link
 * QosException}s and is needed since OkHttp {@link okhttp3.Interceptor}s must only throw {@link IOException}s when
 * handling requests or responses.
 * <p>
 * See {@link QosIoExceptionHandler} for an end-to-end explanation of http-remoting specific client-side error
 * handling.
 */
class QosIoException extends IOException {

    private final QosException qosException;
    private final Response response;

    QosIoException(QosException qosException, Response response) {
        super("Failed to complete the request due to a server-side QoS condition: " + response.code());
        this.qosException = qosException;
        this.response = response;
    }

    QosException getQosException() {
        return qosException;
    }

    /** The OkHttp response that gave rise to this exception. */
    public Response getResponse() {
        return response;
    }
}
