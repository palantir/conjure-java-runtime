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

package com.palantir.remoting2.servers.jersey;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.core.Response;

/**
 * A {@link ContainerRequestFilter} that limits the number of requests per second based on a feature {@code T} of the
 * {@link ContainerRequestContext}. A rate of no more than {@code maxRequestsPerSecond} will be allowed for each unique
 * feature.
 * <p>
 * The features of type {@code T} will be used a cache keys and therefore must be immutable and implement hashCode and
 * equals properly.
 */
public final class RequestRateLimiter<T> implements ContainerRequestFilter {

    private static final int DEFAULT_MAX_FEATURES = 100_000;

    private final Function<ContainerRequestContext, Optional<T>> requestFeatureFunc;
    private final LoadingCache<T, RateLimiter> limiters;

    public static <T> RequestRateLimiter<T> create(long maxRequestsPerSecond,
            Function<ContainerRequestContext, Optional<T>> requestFeatureFunc) {
        return new RequestRateLimiter<>(maxRequestsPerSecond, DEFAULT_MAX_FEATURES, requestFeatureFunc);
    }

    @VisibleForTesting
    RequestRateLimiter(long maxRequestsPerSecond, long maxUniqueFeatures,
            Function<ContainerRequestContext, Optional<T>> requestFeatureFunc) {
        this.requestFeatureFunc = requestFeatureFunc;
        this.limiters = CacheBuilder.newBuilder()
                .maximumSize(maxUniqueFeatures)
                .build(new CacheLoader<T, RateLimiter>() {
                    @Override
                    public RateLimiter load(T key) throws Exception {
                        return RateLimiter.create(maxRequestsPerSecond);
                    }
                });
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        requestFeatureFunc.apply(requestContext)
                .ifPresent(feature -> {
                    if (!limiters.getUnchecked(feature).tryAcquire()) {
                        requestContext.abortWith(Response.status(429).build());
                    }
                });
    }

}
