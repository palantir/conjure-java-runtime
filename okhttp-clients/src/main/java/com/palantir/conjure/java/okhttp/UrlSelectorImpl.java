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

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.palantir.logsafe.UnsafeArg;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import okhttp3.HttpUrl;

final class UrlSelectorImpl implements UrlSelector {

    private static final Duration RANDOMIZE = Duration.ofMinutes(10);

    private final Supplier<List<HttpUrl>> baseUrls;
    private final AtomicReference<HttpUrl> lastBaseUrl;
    private final LoadingCache<HttpUrl, Instant> failedUrls;
    private final boolean useFailedUrlCache;
    private final Clock clock;

    private UrlSelectorImpl(
            ImmutableList<HttpUrl> baseUrls, boolean reshuffle, Duration failedUrlCooldown, Clock clock) {
        Preconditions.checkArgument(!baseUrls.isEmpty(), "Must specify at least one URL");
        Preconditions.checkArgument(!failedUrlCooldown.isNegative(), "Cache expiration must be non-negative");
        if (reshuffle) {
            // Add jitter to avoid mass node reassignment when multiple nodes of a client are restarted
            Duration jitter = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(-30, 30));
            this.baseUrls = Suppliers.memoizeWithExpiration(
                    () -> shuffle(baseUrls),
                    RANDOMIZE.plus(jitter).toMillis(),
                    TimeUnit.MILLISECONDS);
        } else {
            // deterministic for testing only
            this.baseUrls = () -> baseUrls;
        }

        // Assuming that baseUrls is already randomized, start with the first one.
        this.lastBaseUrl = new AtomicReference<>(baseUrls.get(0));

        this.clock = clock;
        this.failedUrls = Caffeine.newBuilder()
                .executor(MoreExecutors.directExecutor())
                .maximumSize(baseUrls.size())
                .build(key -> clock.instant().plus(failedUrlCooldown));
        this.useFailedUrlCache = !failedUrlCooldown.isNegative() && !failedUrlCooldown.isZero();
    }

    /**
     * Creates a new {@link UrlSelector} with the supplied URLs. The order of the URLs are randomized every
     * 10 minutes when {@code randomizeOrder} is set to true, which should be preferred except when
     * testing. If a {@code failedUrlCooldown} is specified, URLs that are marked as failed using
     * {@link #markAsFailed(HttpUrl)} will be removed from the pool of prioritized, healthy URLs for that period of
     * time.
     */
    static UrlSelectorImpl createWithFailedUrlCooldown(
            Collection<String> baseUrls, boolean reshuffle, Duration failedUrlCooldown, Clock clock) {
        ImmutableSet.Builder<HttpUrl> canonicalUrls = ImmutableSet.builder();  // ImmutableSet maintains insert order
        baseUrls.forEach(url -> {
            HttpUrl httpUrl = HttpUrl.parse(switchWsToHttp(url));
            Preconditions.checkArgument(httpUrl != null, "Not a valid URL: %s", url);
            HttpUrl canonicalUrl = canonicalize(httpUrl);
            Preconditions.checkArgument(canonicalUrl.equals(httpUrl),
                    "Base URLs must be 'canonical' and consist of schema, host, port, and path only: %s", url);
            canonicalUrls.add(canonicalUrl);
        });
        return new UrlSelectorImpl(ImmutableList.copyOf(canonicalUrls.build()), reshuffle, failedUrlCooldown, clock);
    }

    @VisibleForTesting
    static UrlSelectorImpl create(Collection<String> baseUrls, boolean reshuffle) {
        return createWithFailedUrlCooldown(baseUrls, reshuffle, Duration.ZERO, Clock.systemUTC());
    }

    static <T> List<T> shuffle(List<T> list) {
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

    /**
     * Attempt to redirect to the given redirectUrl, which could be a longer URL than just a base path, in which
     * case it will first be matched to a baseUrl from {@link #baseUrls}.
     */
    @Override
    public Optional<HttpUrl> redirectTo(HttpUrl current, String redirectUrl) {
        return baseUrlFor(HttpUrl.parse(redirectUrl), baseUrls.get()).flatMap(baseUrl -> redirectTo(current, baseUrl));
    }

    /**
     * Rewrites the current URL to use the new {@code redirectBaseUrl}, if the path prefix is compatible, otherwise
     * it returns {@link Optional#empty()}.
     *
     * Also updates the {@link #lastBaseUrl} with the given {@code redirectBaseUrl}.
     *
     * @param redirectBaseUrl  expected to be an actual base url that exists in {@link #baseUrls}.
     */
    private Optional<HttpUrl> redirectTo(HttpUrl current, HttpUrl redirectBaseUrl) {
        lastBaseUrl.set(redirectBaseUrl);

        if (!isPathPrefixFor(redirectBaseUrl, current)) {
            // The requested redirectBaseUrl has a path that is not compatible with
            // the path of the current URL
            return Optional.empty();
        }
        return Optional.of(current.newBuilder()
                .scheme(redirectBaseUrl.scheme())
                .host(redirectBaseUrl.host())
                .port(redirectBaseUrl.port())
                .encodedPath(
                        redirectBaseUrl.encodedPath()  // matching prefix
                                + current.encodedPath().substring(redirectBaseUrl.encodedPath().length()))
                .build());
    }

    @Override
    public Optional<HttpUrl> redirectToNext(HttpUrl currentUrl) {
        List<HttpUrl> httpUrls = baseUrls.get();
        // if possible, determine the index of the passed in url (so we can be sure to return a url which is different)
        Optional<Integer> currentUrlIndex = indexFor(currentUrl, httpUrls);
        int startIndex = currentUrlIndex.orElseGet(() -> getIndexOfLastBaseUrl(httpUrls));

        Optional<HttpUrl> nextUrl = getNext(startIndex, httpUrls);
        if (nextUrl.isPresent()) {
            return redirectTo(currentUrl, nextUrl.get());
        }

        // No healthy URLs remain; re-balance across any specified nodes
        return redirectTo(currentUrl, httpUrls.get((startIndex + 1) % httpUrls.size()));
    }

    @Override
    public Optional<HttpUrl> redirectToCurrent(HttpUrl current) {
        return redirectTo(current, lastBaseUrl.get());
    }

    @Override
    public Optional<HttpUrl> redirectToNextRoundRobin(HttpUrl current) {
        List<HttpUrl> httpUrls = baseUrls.get();
        // Ignore whatever base URL 'current' might match to, get the last base URL that was used
        int lastIndex = getIndexOfLastBaseUrl(httpUrls);
        Optional<HttpUrl> nextUrl = getNext(lastIndex, httpUrls);
        if (nextUrl.isPresent()) {
            return redirectTo(current, nextUrl.get());
        }

        return redirectTo(current, httpUrls.get((lastIndex + 1) % httpUrls.size()));
    }

    @Override
    public void markAsSucceeded(HttpUrl failedUrl) {
        if (useFailedUrlCache) {
            baseUrlFor(failedUrl, baseUrls.get()).ifPresent(failedUrls::invalidate);
        }
    }

    @Override
    public void markAsFailed(HttpUrl failedUrl) {
        if (useFailedUrlCache) {
            baseUrlFor(failedUrl, baseUrls.get()).ifPresent(failedUrls::refresh);
        }
    }

    /**
     * Returns the index of {@link #lastBaseUrl}, which is expected to exist in {@code httpUrls}.
     */
    private int getIndexOfLastBaseUrl(List<HttpUrl> httpUrls) {
        int index = httpUrls.indexOf(lastBaseUrl.get());
        Preconditions.checkState(index != -1,
                "Expected httpUrls to contain currentBaseUrl",
                UnsafeArg.of("httpUrls", httpUrls),
                UnsafeArg.of("currentBaseUrl", lastBaseUrl));
        return index;
    }

    /** Get the next URL in {@code baseUrls}, after the supplied index, that has not been marked as failed. */
    private Optional<HttpUrl> getNext(int startIndex, List<HttpUrl> httpUrls) {
        int urlIndex = startIndex;
        int index = startIndex;

        for (int numAttempts = 0; numAttempts < httpUrls.size(); numAttempts++) {
            urlIndex = (urlIndex + 1) % httpUrls.size();
            HttpUrl httpUrl = httpUrls.get(urlIndex);

            Instant cooldownFinished = failedUrls.getIfPresent(httpUrl);
            if (cooldownFinished != null) {
                // continue to the next URL if the cooldown has not elapsed
                if (clock.instant().isBefore(cooldownFinished)) {
                    continue;
                }

                // use the failed URL once and refresh to ensure that the cooldown elapses before it is used again
                failedUrls.refresh(httpUrl);
            }

            return Optional.of(httpUrls.get(urlIndex));
        }

        return Optional.empty();
    }

    private static Optional<HttpUrl> baseUrlFor(HttpUrl url, List<HttpUrl> currentUrls) {
        return indexFor(url, currentUrls).map(currentUrls::get);
    }

    private static Optional<Integer> indexFor(HttpUrl url, List<HttpUrl> currentUrls) {
        HttpUrl canonicalUrl = canonicalize(url);
        for (int i = 0; i < currentUrls.size(); ++i) {
            if (isBaseUrlFor(currentUrls.get(i), canonicalUrl)) {
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
}
