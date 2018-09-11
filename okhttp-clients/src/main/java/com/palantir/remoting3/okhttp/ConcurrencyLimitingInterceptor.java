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

import com.netflix.concurrency.limits.Limiter;
import com.netflix.concurrency.limits.limit.VegasLimit;
import com.netflix.concurrency.limits.limiter.BlockingLimiter;
import com.netflix.concurrency.limits.limiter.DefaultLimiter;
import com.netflix.concurrency.limits.strategy.SimpleStrategy;
import com.palantir.remoting.api.errors.QosException;
import java.io.IOException;
import java.util.Optional;
import okhttp3.Interceptor;
import okhttp3.Response;

public final class ConcurrencyLimitingInterceptor implements Interceptor {

    private final DefaultLimiter<Void> delegate = DefaultLimiter.newBuilder()
            .limit(VegasLimit.newDefault())
            .build(new SimpleStrategy<>());
    private final BlockingLimiter<Void> limiter = BlockingLimiter.wrap(delegate);

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
}
