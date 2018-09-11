/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import com.google.common.util.concurrent.RateLimiter;
import com.netflix.concurrency.limits.Limit;
import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.Strategy;
import com.netflix.concurrency.limits.limit.AIMDLimit;
import com.netflix.concurrency.limits.limiter.DefaultLimiter;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.Interceptor;
import okhttp3.Response;

public final class LimitingInterceptor implements Interceptor {

    private final DefaultLimiter<Void> limiter = DefaultLimiter.newBuilder()
            .windowSize(20)
            .limit(new AimdLimit(20, 0.8))
            .build(new RateLimiterStrategy());

    @Override
    public Response intercept(Chain chain) throws IOException {
        Limiter.Listener listener = limiter.acquire(null).orElseThrow(IllegalStateException::new);

        Response response = chain.call().execute();

        Optional<QosException> exception = QosExceptionResponseHandler.INSTANCE.handle(response);
        if (exception.isPresent()) {
            exception.get().accept(new QosVisitor(listener));
        } else {
            listener.onSuccess();
        }

        return response;
    }

    private static final class QosVisitor implements QosException.Visitor<Void> {
        private final Limiter.Listener listener;

        QosVisitor(Limiter.Listener listener) {
            this.listener = listener;
        }

        @Override
        public Void visit(QosException.Throttle exception) {
            listener.onDropped();
            return null;
        }

        @Override
        public Void visit(QosException.RetryOther exception) {
            listener.onIgnore();
            return null;
        }

        @Override
        public Void visit(QosException.Unavailable exception) {
            listener.onDropped();
            return null;
        }
    }

    /**
     * Blocks on acquiring a permit from a local {@link RateLimiter}.
     */
    private static final class RateLimiterStrategy implements Strategy<Void> {
        private final RateLimiter limiter = RateLimiter.create(1);
        private final AtomicInteger inFlight = new AtomicInteger(0);

        @Override
        public Token tryAcquire(Void context) {
            limiter.acquire();
            return Token.newAcquired(inFlight.incrementAndGet(), inFlight::decrementAndGet);
        }

        @Override
        public void setLimit(int limit) {
            limiter.setRate(limit);
        }
    }

    /**
     * Same as {@link AIMDLimit} except increases the limit whenever maxInFlight is equal to limit, rather than greater
     * than limit.
     */
    private static final class AimdLimit implements Limit {
        private final double backoffRatio;
        private int limit;

        AimdLimit(int initLimit, double backoffRatio) {
            this.limit = initLimit;
            this.backoffRatio = backoffRatio;
        }

        @Override
        public int getLimit() {
            return limit;
        }

        @Override
        public void update(SampleWindow sample) {
            if (sample.didDrop()) {
                limit = Math.max(1, Math.min(limit - 1, (int) (limit * backoffRatio)));
            } else if (sample.getMaxInFlight() == limit) {
                limit = limit + 1;
            }
        }
    }
}
