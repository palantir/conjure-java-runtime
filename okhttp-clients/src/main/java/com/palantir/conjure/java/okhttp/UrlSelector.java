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

import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;

public interface UrlSelector {
    /**
     * Given a (non-base) URL {@code current}, returns the URL obtained from {@code current} by replacing scheme, host,
     * port, and path prefix with those of the given {@code baseUrl}. For example, assume {@code current =
     * http://foo.com/a/b/id=123} and {@code baseUrl = https://bar.org:8080/a}; then this method returns {@code
     * https://bar.org:8080/a/b/id=123}. Returns {@link Optional#empty} if there is no {@link #getBaseUrls base URL} for
     * which such a substitution can be made, for example, if there is no base URL whose {@link HttpUrl#encodedPath
     * path} is a prefix to the path of the {@code current} URL.
     *
     * <p>Changes the "current" URL of this selector so that that a subsequent call to {@link #redirectToCurrent} yields
     * the same base address as the URL returned by this call.
     */
    Optional<HttpUrl> redirectTo(HttpUrl requestUrl, String redirectBaseUrl);

    /**
     * Similar to {@link #redirectTo}, but redirects the given URL to the next (in some undefined order) {@link
     * #getBaseUrls baseURL} after the supplied {@code current} URL.
     */
    Optional<HttpUrl> redirectToNext(HttpUrl requestUrl);

    /** Similar to {@link #redirectTo}, but redirects the given URL to the current {@link #getBaseUrls baseURL}. */
    Optional<HttpUrl> redirectToCurrent(HttpUrl requestUrl);

    /**
     * Similar to {@link #redirectTo}, but redirects the given URL to the next (in some undefined order) after the last
     * URL used.
     */
    Optional<HttpUrl> redirectToNextRoundRobin(HttpUrl requestUrl);

    /**
     * Returns the base URLs that this UrlSelector matches against. Note that implementations should parse web socket
     * (ws:// and ws:///) URLs as http (http:// and https:// respectively), in a similar to how {@link
     * okhttp3.Request#url} does.
     */
    List<HttpUrl> getBaseUrls();

    /**
     * Indicates that a call against the given URL has succeeded. Implementations can use success statistics to
     * determine when a previously unavailable host is once again available and should be used for future calls.
     */
    void markAsSucceeded(HttpUrl succeededUrl);

    /**
     * Indicates that a call against the given URL has failed. Implementations can use failure statistics to determine
     * which hosts may be unavailable and should be avoided for future calls.
     */
    void markAsFailed(HttpUrl failedUrl);
}
