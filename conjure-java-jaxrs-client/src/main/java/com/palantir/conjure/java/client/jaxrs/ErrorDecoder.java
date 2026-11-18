/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.conjure.java.client.jaxrs;

/**
 * Allows you to massage an exception into a application-specific one. Converting out to a throttle
 * exception are examples of this in use.
 *
 * <p/>Ex:
 * <pre>
 * class IllegalArgumentExceptionOn404Decoder extends ErrorDecoder {
 *
 *   &#064;Override
 *   public Exception decode(String methodKey, Response response) {
 *    if (response.status() == 400)
 *        throw new IllegalArgumentException(&quot;bad zone name&quot;);
 *    return new ErrorDecoder.Default().decode(methodKey, request, response);
 *   }
 *
 * }
 * </pre>
 *
 * <p/><b>Error handling</b>
 *
 * <p/>Responses where {@link Response#status()} is not in the 2xx
 * range are classified as errors, addressed by the {@link ErrorDecoder}. That said, certain RPC
 * apis return errors defined in the {@link Response#body()} even on a 200 status. For example, in
 * the DynECT api, a job still running condition is returned with a 200 status, encoded in json.
 * When scenarios like this occur, you should raise an application-specific exception.
 */
interface ErrorDecoder {

    /**
     * Implement this method in order to decode an HTTP {@link Response} when {@link
     * Response#status()} is not in the 2xx range. Please raise  application-specific exceptions where
     * possible.
     *
     * @param response  HTTP response where {@link Response#status() status} is greater than or equal
     *                  to {@code 300}.
     * @return Exception IOException, if there was a network error reading the response or an
     * application-specific exception decoded by the implementation.
     */
    Exception decode(Response response);
}
