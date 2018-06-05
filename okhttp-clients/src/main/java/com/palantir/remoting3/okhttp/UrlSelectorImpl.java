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

package com.palantir.remoting3.okhttp;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.HttpUrl;

final class UrlSelectorImpl implements UrlSelector {

    private final ImmutableList<HttpUrl> baseUrls;
    private final AtomicInteger currentUrl;
    private final Cache<HttpUrl, Boolean> failedUrls;
    private final boolean useFailedUrlCache;

    private UrlSelectorImpl(ImmutableList<HttpUrl> baseUrls, long duration, TimeUnit unit) {
        this.baseUrls = baseUrls;
        this.currentUrl = new AtomicInteger(0);
        this.failedUrls = CacheBuilder.newBuilder()
                .maximumSize(baseUrls.size())
                .expireAfterWrite(duration, unit)
                .build();
        this.useFailedUrlCache = duration > 0L;

        Preconditions.checkArgument(!baseUrls.isEmpty(), "Must specify at least one URL");
        Preconditions.checkArgument(duration >= 0L, "Cache expiration must be non-negative");
    }

    /**
     * Creates a new {@link UrlSelector} with the supplied URLs. The order of the URLs may be randomized by setting
     * {@code randomizeOrder} to true. If a non-zero {@code duration} and {@code timeUnit} are specified, URLs that are
     * marked as failed using {@link #markAsFailed(HttpUrl)} will be removed from the pool of prioritized, healthy URLs
     * for that period of time.
     */
    static UrlSelectorImpl create(Collection<String> baseUrls, boolean randomizeOrder, long duration,
            TimeUnit timeUnit) {
        List<String> orderedUrls = new ArrayList<>(baseUrls);
        if (randomizeOrder) {
            Collections.shuffle(orderedUrls);
        }

        ImmutableSet.Builder<HttpUrl> canonicalUrls = ImmutableSet.builder();  // ImmutableSet maintains insert order
        orderedUrls.forEach(url -> {
            HttpUrl httpUrl = HttpUrl.parse(switchWsToHttp(url));
            Preconditions.checkArgument(httpUrl != null, "Not a valid URL: %s", url);
            HttpUrl canonicalUrl = canonicalize(httpUrl);
            Preconditions.checkArgument(canonicalUrl.equals(httpUrl),
                    "Base URLs must be 'canonical' and consist of schema, host, port, and path only: %s", url);
            canonicalUrls.add(canonicalUrl);
        });
        return new UrlSelectorImpl(ImmutableList.copyOf(canonicalUrls.build()), duration, timeUnit);
    }

    static UrlSelectorImpl create(Collection<String> baseUrls, boolean randomizeOrder) {
        return create(baseUrls, randomizeOrder, 0L, TimeUnit.MILLISECONDS);
    }

    private static String switchWsToHttp(String url) {
        // Silently replace web socket URLs with HTTP URLs. See https://github.com/square/okhttp/issues/1652.
        if (url.regionMatches(true, 0, "ws:", 0, 3)) {
            return "http:" + url.substring(3);
        } else if (url.regionMatches(true, 0, "wss:", 0, 4)) {
            return "https:" + url.substring(4);
        } else {
            return url;
        }
    }

    @Override
    public Optional<HttpUrl> redirectTo(HttpUrl current, String redirectBaseUrl) {
        return redirectTo(current, HttpUrl.parse(redirectBaseUrl));
    }

    private Optional<HttpUrl> redirectTo(HttpUrl current, HttpUrl redirectBaseUrl) {
        Optional<Integer> baseUrlIndex = indexFor(redirectBaseUrl);
        baseUrlIndex.ifPresent(currentUrl::set);

        return baseUrlIndex
                .map(baseUrls::get)
                .flatMap(baseUrl -> {
                    if (!isPathPrefixFor(baseUrl, current)) {
                        // The requested redirectBaseUrl has a path that is not compatible with
                        // the path of the current URL
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
    public Optional<HttpUrl> redirectToNext(HttpUrl existingUrl) {
        // if possible, determine the index of the passed in url (so we can be sure to return a url which is different)
        Optional<Integer> existingUrlIndex = indexFor(existingUrl);

        int numAttempts = 0;
        int potentialNextIndex = existingUrlIndex.orElse(currentUrl.get());

        // Find the next URL that is not marked as failed
        while (numAttempts < baseUrls.size()) {
            potentialNextIndex = (potentialNextIndex + 1) % baseUrls.size();
            Boolean maybeInCache = failedUrls.getIfPresent(baseUrls.get(potentialNextIndex));
            if (maybeInCache == null) {
                return redirectTo(existingUrl, baseUrls.get(potentialNextIndex));
            }
            numAttempts++;
        }

        // No healthy URLs remain; re-balance across any specified nodes
        return redirectTo(existingUrl,
                baseUrls.get((existingUrlIndex.orElse(currentUrl.get()) + 1) % baseUrls.size()));
    }

    @Override
    public Optional<HttpUrl> redirectToCurrent(HttpUrl current) {
        return redirectTo(current, baseUrls.get(currentUrl.get()));
    }

    @Override
    public void markAsFailed(HttpUrl failedUrl) {
        if (useFailedUrlCache) {
            failedUrls.put(failedUrl, true);
        }
    }

    private Optional<Integer> indexFor(HttpUrl url) {
        HttpUrl canonicalUrl = canonicalize(url);
        for (int i = 0; i < baseUrls.size(); ++i) {
            if (isBaseUrlFor(baseUrls.get(i), canonicalUrl)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
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
