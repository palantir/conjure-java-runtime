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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;

final class UrlSelectorImpl implements UrlSelector {

    private final ImmutableList<HttpUrl> baseUrls;
    private final AtomicInteger currentUrl;

    private UrlSelectorImpl(ImmutableList<HttpUrl> baseUrls) {
        this.baseUrls = baseUrls;
        this.currentUrl = new AtomicInteger(0);
    }

    static UrlSelectorImpl create(Collection<String> baseUrls) {
        ImmutableSet.Builder<HttpUrl> canonicalUrls = ImmutableSet.builder();
        baseUrls.forEach(url -> {
            HttpUrl httpUrl = HttpUrl.parse(url);
            Preconditions.checkArgument(httpUrl != null, "Not a valid URL: %s", url);
            HttpUrl canonicalUrl = canonicalize(httpUrl);
            Preconditions.checkArgument(canonicalUrl.equals(httpUrl),
                    "Base URLs must be 'canonical' and consist of schema, host, port, and path only: %s", url);
            canonicalUrls.add(canonicalUrl);
        });
        return new UrlSelectorImpl(ImmutableList.copyOf(canonicalUrls.build()));
    }

    @Override
    public Optional<HttpUrl> redirectTo(HttpUrl current, String redirectBaseUrl) {
        return redirectTo(current, HttpUrl.parse(redirectBaseUrl));
    }

    private Optional<HttpUrl> redirectTo(HttpUrl current, HttpUrl redirectBaseUrl) {
        return baseUrlFor(redirectBaseUrl).flatMap(baseUrl -> {
            if (!isPathPrefixFor(baseUrl, current)) {
                // The request redirectBaseUrl has a path that is not compatible with the path of the current URL
                return Optional.empty();
            } else {
                return Optional.of(current.newBuilder()
                        .scheme(baseUrl.scheme())
                        .host(baseUrl.host())
                        .port(baseUrl.port())
                        .encodedPath(
                                baseUrl.encodedPath()  // matching prefix
                                        + current.encodedPath().substring(baseUrl.encodedPath().length()))
                        .build());
            }
        });
    }

    @Override
    public Optional<HttpUrl> redirectToNext(HttpUrl current) {
        int index = currentUrl.updateAndGet(operand -> (operand + 1) % baseUrls.size());
        return redirectTo(current, baseUrls.get(index));
    }

    private Optional<HttpUrl> baseUrlFor(HttpUrl url) {
        HttpUrl canonicalUrl = canonicalize(url);
        return baseUrls.stream()
                .filter(baseUrl -> isBaseUrlFor(baseUrl, canonicalUrl))
                .findFirst();
    }

    /**
     * Returns true if the canonicalized base URLs are equal and if the path of the {@code prefixUrl} is a prefix (in
     * the string sense) of the path of the given {@code fullUrl}.
     */
    @VisibleForTesting
    static boolean isBaseUrlFor(HttpUrl baseUrl, HttpUrl fullUrl) {
        return fullUrl.scheme().equals(baseUrl.scheme())
                && fullUrl.host().equals(baseUrl.host())
                && fullUrl.port() == baseUrl.port()
                && isPathPrefixFor(baseUrl, fullUrl);
    }

    /** Returns true if the path of the given {@code baseUrl} is a prefix of the path of the given {@code fullUrl}. */
    private static boolean isPathPrefixFor(HttpUrl baseUrl, HttpUrl fullUrl) {
        return fullUrl.encodedPath().startsWith(baseUrl.encodedPath());
    }

    /** Returns the "canonical" part of the given URL, consisting of schema, host, port, and path only. */
    private static HttpUrl canonicalize(HttpUrl baseUrl) {
        return new HttpUrl.Builder()
                .scheme(baseUrl.scheme())
                .host(baseUrl.host())
                .port(baseUrl.port())
                .encodedPath(baseUrl.encodedPath())
                .build();
    }

    @Override
    public ImmutableList<HttpUrl> getBaseUrls() {
        return baseUrls;
    }
}
