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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import okhttp3.HttpUrl;

final class UrlSelectorImpl implements UrlSelector {

    private static final Duration RANDOMIZE = Duration.ofMinutes(10);

    private final Supplier<List<HttpUrl>> baseUrls;
    private final AtomicInteger currentUrl;
    private final Cache<HttpUrl, UrlAvailability> failedUrls;
    private final boolean useFailedUrlCache;

    private UrlSelectorImpl(ImmutableList<HttpUrl> baseUrls, boolean randomizeOrder, Duration failedUrlCooldown) {
        if (randomizeOrder) {
            // Add jitter to avoid mass node reassignment when multiple nodes of a client are restarted
            Duration jitter = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(-30, 30));
            this.baseUrls = Suppliers.memoizeWithExpiration(
                    () -> randomize(baseUrls),
                    RANDOMIZE.plus(jitter).toMillis(),
                    TimeUnit.MILLISECONDS);
        } else {
            // deterministic for testing only
            this.baseUrls = () -> baseUrls;
        }

        this.currentUrl = new AtomicInteger(0);

        long coolDownMillis = failedUrlCooldown.toMillis();
        this.failedUrls = Caffeine.newBuilder()
                .maximumSize(baseUrls.size())
                .expireAfterWrite(coolDownMillis, TimeUnit.MILLISECONDS)
                .build();
        this.useFailedUrlCache = !failedUrlCooldown.isNegative() && !failedUrlCooldown.isZero();

        Preconditions.checkArgument(!baseUrls.isEmpty(), "Must specify at least one URL");
        Preconditions.checkArgument(!failedUrlCooldown.isNegative(), "Cache expiration must be non-negative");
    }

    /**
     * Creates a new {@link UrlSelector} with the supplied URLs. The order of the URLs are randomized every
     * 10 minutes when {@code randomizeOrder} is set to true, which should be preferred except when
     * testing. If a {@code failedUrlCooldown} is specified, URLs that are marked as failed using
     * {@link #markAsFailed(HttpUrl)} will be removed from the pool of prioritized, healthy URLs for that period of
     * time.
     */
    static UrlSelectorImpl createWithFailedUrlCooldown(Collection<String> baseUrls, boolean randomizeOrder,
            Duration failedUrlCooldown) {
        ImmutableSet.Builder<HttpUrl> canonicalUrls = ImmutableSet.builder();  // ImmutableSet maintains insert order
        baseUrls.forEach(url -> {
            HttpUrl httpUrl = HttpUrl.parse(switchWsToHttp(url));
            Preconditions.checkArgument(httpUrl != null, "Not a valid URL: %s", url);
            HttpUrl canonicalUrl = canonicalize(httpUrl);
            Preconditions.checkArgument(canonicalUrl.equals(httpUrl),
                    "Base URLs must be 'canonical' and consist of schema, host, port, and path only: %s", url);
            canonicalUrls.add(canonicalUrl);
        });
        return new UrlSelectorImpl(ImmutableList.copyOf(canonicalUrls.build()), randomizeOrder, failedUrlCooldown);
    }

    static UrlSelectorImpl create(Collection<String> baseUrls, boolean randomizeOrder) {
        return createWithFailedUrlCooldown(baseUrls, randomizeOrder, Duration.ZERO);
    }

    private static <T> List<T> randomize(ImmutableList<T> list) {
        List<T> shuffledList = new ArrayList<>(list);
        Collections.shuffle(shuffledList);
        return Collections.unmodifiableList(shuffledList);
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
                .map(baseUrls.get()::get)
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

        int potentialNextIndex = existingUrlIndex.orElse(currentUrl.get());

        Optional<HttpUrl> nextUrl = getNext(potentialNextIndex);
        if (nextUrl.isPresent()) {
            return redirectTo(existingUrl, nextUrl.get());
        }

        // No healthy URLs remain; re-balance across any specified nodes
        List<HttpUrl> httpUrls = baseUrls.get();
        return redirectTo(existingUrl,
                httpUrls.get((existingUrlIndex.orElse(currentUrl.get()) + 1) % httpUrls.size()));
    }

    @Override
    public Optional<HttpUrl> redirectToCurrent(HttpUrl current) {
        return redirectTo(current, baseUrls.get().get(currentUrl.get()));
    }

    @Override
    public Optional<HttpUrl> redirectToNextRoundRobin(HttpUrl current) {
        Optional<HttpUrl> nextUrl = getNext(currentUrl.get());
        if (nextUrl.isPresent()) {
            return redirectTo(current, nextUrl.get());
        }

        List<HttpUrl> httpUrls = baseUrls.get();
        return redirectTo(current, httpUrls.get((currentUrl.get() + 1) % httpUrls.size()));
    }

    @Override
    public void markAsFailed(HttpUrl failedUrl) {
        if (useFailedUrlCache) {
            Optional<Integer> indexForFailedUrl = indexFor(failedUrl);
            indexForFailedUrl.ifPresent(index ->
                    failedUrls.put(baseUrls.get().get(index), UrlAvailability.FAILED)
            );
        }
    }

    /** Get the next URL in {@code baseUrls}, after the supplied index, that has not been marked as failed. */
    private Optional<HttpUrl> getNext(int startIndex) {
        int numAttempts = 0;
        int index = startIndex;
        List<HttpUrl> httpUrls = baseUrls.get();

        // Find the next URL that is not marked as failed
        while (numAttempts < httpUrls.size()) {
            index = (index + 1) % httpUrls.size();
            UrlAvailability isFailed = failedUrls.getIfPresent(httpUrls.get(index));
            if (isFailed == null) {
                return Optional.of(httpUrls.get(index));
            }
            numAttempts++;
        }

        return Optional.empty();
    }

    private Optional<Integer> indexFor(HttpUrl url) {
        HttpUrl canonicalUrl = canonicalize(url);
        List<HttpUrl> httpUrls = baseUrls.get();
        for (int i = 0; i < httpUrls.size(); ++i) {
            if (isBaseUrlFor(httpUrls.get(i), canonicalUrl)) {
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
    public List<HttpUrl> getBaseUrls() {
        return baseUrls.get();
    }

    private enum UrlAvailability {

        /**
         * URL has been marked as failed.
         */
        FAILED
    }
}
