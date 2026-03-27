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
package com.palantir.conjure.java.client.jaxrs.feign;

/**
 * <br><br><b>relationship to JAXRS 2.0</b><br> <br> Similar to {@code
 * javax.ws.rs.client.WebTarget}, as it produces requests. However, {@link RequestTemplate} is a
 * closer match to {@code WebTarget}.
 *
 * @param <T> type of the interface this target applies to.
 */
public interface Target<T> {

    /** The type of the interface this target applies to. ex. {@code Route53}. */
    Class<T> type();

    /** base HTTP URL of the target. For example, {@code https://api/v2}. */
    String url();

    /**
     * Targets a template to this target, adding the {@link #url() base url} and any target-specific
     * headers or query parameters. <br> <br> For example: <br>
     * <pre>
     * public Request apply(RequestTemplate input) {
     *     input.insert(0, url());
     *     input.replaceHeader(&quot;X-Auth&quot;, currentToken);
     *     return input.asRequest();
     * }
     * </pre>
     * <br> <br><br><b>relationship to JAXRS 2.0</b><br> <br> This call is similar to {@code
     * javax.ws.rs.client.WebTarget.request()}, except that we expect transient, but necessary
     * decoration to be applied on invocation.
     */
    Request apply(RequestTemplate input);
}
