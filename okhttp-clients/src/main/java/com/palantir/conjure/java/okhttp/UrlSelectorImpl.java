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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.UnsafeArg;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import okhttp3.HttpUrl;

final class UrlSelectorImpl implements UrlSelector {

    private static final Duration RANDOMIZE = Duration.ofMinutes(10);

    private final Supplier<List<HttpUrl>> baseUrls;
    private final AtomicReference<HttpUrl> lastBaseUrl;
    private final Map<HttpUrl, Instant> failedUrls;
    private final boolean useFailedUrlCache;
    private final Clock clock;
    private final Duration failedUrlCooldown;

    private UrlSelectorImpl(
            ImmutableList<HttpUrl> baseUrls, boolean reshuffle, Duration failedUrlCooldown, Clock clock) {
        Preconditions.checkArgument(!baseUrls.isEmpty(), "Must specify at least one URL");
        Preconditions.checkArgument(!failedUrlCooldown.isNegative(), "Cache expiration must be non-negative");
        if (reshuffle) {
            // Add jitter to avoid mass node reassignment when multiple nodes of a client are restarted
            Duration jitter = Duration.ofSeconds(ThreadLocalRandom.current().nextLong(-30, 30));
            this.baseUrls = Suppliers.memoizeWithExpiration(
                    () -> shuffle(baseUrls), RANDOMIZE.plus(jitter).toMillis(), TimeUnit.MILLISECONDS);
        } else {
            // deterministic for testing only
            this.baseUrls = () -> baseUrls;
        }

        // Assuming that baseUrls is already randomized, start with the first one.
        this.lastBaseUrl = new AtomicReference<>(baseUrls.get(0));

        this.clock = clock;
        this.failedUrlCooldown = failedUrlCooldown;
        this.failedUrls = new ConcurrentHashMap<>(baseUrls.size());
        this.useFailedUrlCache = !failedUrlCooldown.isNegative() && !failedUrlCooldown.isZero();
    }

    /**
     * Creates a new {@link UrlSelector} with the supplied URLs. The order of the URLs are randomized every 10 minutes
     * when {@code randomizeOrder} is set to true, which should be preferred except when testing. If a {@code
     * failedUrlCooldown} is specified, URLs that are marked as failed using {@link #markAsFailed(HttpUrl)} will be
     * removed from the pool of prioritized, healthy URLs for that period of time.
     */
    static UrlSelectorImpl createWithFailedUrlCooldown(
            Collection<String> baseUrls, boolean reshuffle, Duration failedUrlCooldown, Clock clock) {
        ImmutableSet.Builder<HttpUrl> canonicalUrls = ImmutableSet.builder(); // ImmutableSet maintains insert order
        baseUrls.forEach(url -> {
            HttpUrl httpUrl = HttpUrl.parse(switchWsToHttp(url));
            Preconditions.checkArgument(httpUrl != null, "Not a valid URL", UnsafeArg.of("url", url));
            HttpUrl canonicalUrl = canonicalize(httpUrl);
            Preconditions.checkArgument(
                    canonicalUrl.equals(httpUrl),
                    "Base URLs must be 'canonical' and consist of schema, host, port, and path only",
                    UnsafeArg.of("url", url));
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
     * Attempt to redirect to the given redirectUrl, which could be a longer URL than just a base path, in which case it
     * will first be matched to a baseUrl from {@link #baseUrls}.
     */
    @Override
    public Optional<HttpUrl> redirectTo(HttpUrl requestUrl, String redirectUrl) {
        return baseUrlFor(HttpUrl.parse(redirectUrl), baseUrls.get())
                .flatMap(baseUrl -> redirectTo(requestUrl, baseUrl));
    }

    /**
     * Rewrites the request URL to use the new {@code redirectBaseUrl}, if the path prefix is compatible, otherwise it
     * returns {@link Optional#empty()}.
     *
     * <p>Also updates the {@link #lastBaseUrl} with the given {@code redirectBaseUrl}.
     *
     * @param redirectBaseUrl expected to be an actual base url that exists in {@link #baseUrls}.
     */
    private Optional<HttpUrl> redirectTo(HttpUrl requestUrl, HttpUrl redirectBaseUrl) {
        lastBaseUrl.set(redirectBaseUrl);

        if (!isPathPrefixFor(redirectBaseUrl, requestUrl)) {
            // The requested redirectBaseUrl has a path that is not compatible with
            // the path of the request URL
            return Optional.empty();
        }

        return Optional.of(
                requestUrl
                        .newBuilder()
                        .scheme(redirectBaseUrl.scheme())
                        .host(redirectBaseUrl.host())
                        .port(redirectBaseUrl.port())
                        .encodedPath(redirectBaseUrl.encodedPath() // matching prefix
                                + requestUrl.encodedPath().substring(redirectBaseUrl.encodedPath().length()))
                        .build());
    }

    @Override
    public Optional<HttpUrl> redirectToNext(HttpUrl requestUrl) {
        List<HttpUrl> httpUrls = baseUrls.get();

        // If possible, determine the index of the request URL (so we can be sure to redirect to a different URL)
        int lastIndex = indexFor(requestUrl, httpUrls).orElseGet(() -> indexForLastBaseUrl(httpUrls));

        int nextIndex = increment(lastIndex, httpUrls);

        HttpUrl next = getNextHealthy(nextIndex, httpUrls).orElseGet(() -> httpUrls.get(nextIndex));
        return redirectTo(requestUrl, next);
    }

    @Override
    public Optional<HttpUrl> redirectToCurrent(HttpUrl requestUrl) {
        List<HttpUrl> httpUrls = baseUrls.get();

        int startIndex = indexForLastBaseUrl(httpUrls);

        HttpUrl next = getNextHealthy(startIndex, httpUrls).orElseGet(() -> {
            // Revert to round robin behaviour if _all_ nodes have been marked as unhealthy
            int nextIndex = increment(startIndex, httpUrls);
            return httpUrls.get(nextIndex);
        });
        return redirectTo(requestUrl, next);
    }

    @Override
    public Optional<HttpUrl> redirectToNextRoundRobin(HttpUrl requestUrl) {
        List<HttpUrl> httpUrls = baseUrls.get();

        // Ignore whatever base URL the request URL might match to, use the last base URL instead
        int lastIndex = indexForLastBaseUrl(httpUrls);

        int nextIndex = increment(lastIndex, httpUrls);

        HttpUrl next = getNextHealthy(nextIndex, httpUrls).orElseGet(() -> httpUrls.get(nextIndex));
        return redirectTo(requestUrl, next);
    }

    @Override
    public void markAsSucceeded(HttpUrl succeededUrl) {
        if (useFailedUrlCache) {
            baseUrlFor(succeededUrl, baseUrls.get()).ifPresent(failedUrls::remove);
        }
    }

    @Override
    public void markAsFailed(HttpUrl failedUrl) {
        if (useFailedUrlCache) {
            baseUrlFor(failedUrl, baseUrls.get()).ifPresent(this::markBaseUrlAsFailed);
        }
    }

    private void markBaseUrlAsFailed(HttpUrl key) {
        failedUrls.put(key, clock.instant().plus(this.failedUrlCooldown));
    }

    private int indexForLastBaseUrl(List<HttpUrl> httpUrls) {
        // Fallback to index 0 if last base URL is no longer present in base URLs
        return indexFor(lastBaseUrl.get(), httpUrls).orElse(0);
    }

    /**
     * Get the next URL in {@code baseUrls}, after the supplied index.
     *
     * <p>If the {@code failedUrlCooldown} is positive, then this method will skip over nodes that have failed if it's
     * been less than {@code failedUrlCooldown} since they failed. Furthermore, if a node had previously failed but the
     * cooldown has since elapsed, that node's URL will be returned but it will once again be marked as failed (so that
     * it's only tried once).
     */
    private Optional<HttpUrl> getNextHealthy(int startIndex, List<HttpUrl> httpUrls) {
        for (int i = startIndex; i < startIndex + httpUrls.size(); i++) {
            HttpUrl httpUrl = httpUrls.get(i % httpUrls.size());

            Instant cooldownFinished = failedUrls.get(httpUrl);
            if (cooldownFinished != null) {
                // continue to the next URL if the cooldown has not elapsed
                if (clock.instant().isBefore(cooldownFinished)) {
                    continue;
                }

                // use the failed URL once and refresh to ensure that the cooldown elapses before it is used again
                markBaseUrlAsFailed(httpUrl);
            }

            return Optional.of(httpUrl);
        }

        return Optional.empty();
    }

    private static Optional<HttpUrl> baseUrlFor(HttpUrl url, List<HttpUrl> httpUrls) {
        return indexFor(url, httpUrls).map(httpUrls::get);
    }

    private static Optional<Integer> indexFor(HttpUrl url, List<HttpUrl> httpUrls) {
        HttpUrl canonicalUrl = canonicalize(url);
        for (int i = 0; i < httpUrls.size(); ++i) {
            if (isBaseUrlFor(httpUrls.get(i), canonicalUrl)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    private static int increment(int index, List<HttpUrl> urls) {
        return (index + 1) % urls.size();
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
    private static HttpUrl canonicalize(HttpUrl url) {
        return new HttpUrl.Builder()
                .scheme(url.scheme())
                .host(url.host())
                .port(url.port())
                .encodedPath(url.encodedPath())
                .build();
    }

    @Override
    public List<HttpUrl> getBaseUrls() {
        return baseUrls.get();
    }
}
