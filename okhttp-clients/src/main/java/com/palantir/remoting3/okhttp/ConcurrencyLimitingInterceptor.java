package com.palantir.remoting3.okhttp;

import com.google.common.collect.ImmutableSet;
import com.netflix.concurrency.limits.Limiter;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSource;

/**
 * WIP docs for benefit of reviewer.
 *
 * An interceptor for limiting the concurrency of requests to an endpoint.
 *
 * Requests must be tagged (before reaching this point) with a ConcurrencyLimitTag. At this point, we block on
 * receiving a permit to run the request, and store the listener in the tag.
 *
 * When we see evidence of being dropped, we write this into the tag, and when the request retries again the permit
 * will be returned to the pool before acquiring a new one.
 *
 * Users must also wrap the final callback they use; this is used in two ways; first it clears the state in case of
 * failure, secondly on success it will wait until the response is closed before handing back permits. In other words,
 * if you have a server with a concurrency limit (e.g. it is CPU bound), clients should respect the server's
 * concurrency limit.
 *
 * This has a timeout of 1 minute (before an error is logged) in order to try to catch people who have leaked responses
 * (which here will deadlock otherwise). It indicates an application bug every time, but might affect users poorly.
 * I'm happy to remove it, but think there should probably be another solution?
 */
final class ConcurrencyLimitingInterceptor implements Interceptor {
    private static final ImmutableSet<Integer> DROPPED_CODES = ImmutableSet.of(429, 503);

    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters();

    @Override
    public Response intercept(Chain chain) throws IOException {
        Limiter.Listener listener = limiters.limiter(chain.request());
        try {
            Response response = chain.proceed(chain.request());
            if (DROPPED_CODES.contains(response.code())) {
                listener.onDropped();
                return response;
            } else if (!response.isSuccessful() || response.isRedirect()) {
                listener.onIgnore();
                return response;
            } else {
                return wrapResponse(listener, response);
            }
        } catch (IOException e) {
            listener.onIgnore();
            throw e;
        }
    }

    private static Response wrapResponse(Limiter.Listener listener, Response response) {
        if (response.body() == null) {
            return response;
        }
        ResponseBody currentBody = response.body();
        ResponseBody newResponseBody =
                ResponseBody.create(currentBody.contentType(), currentBody.contentLength(),
                        new ReleaseConcurrencyLimitBufferedSource(currentBody.source(), listener));
        return response.newBuilder()
                .body(newResponseBody)
                .build();
    }

    private static final class ReleaseConcurrencyLimitBufferedSource extends ForwardingBufferedSource {
        private final BufferedSource delegate;
        private final Limiter.Listener listener;

        private ReleaseConcurrencyLimitBufferedSource(BufferedSource delegate, Limiter.Listener listener) {
            super(delegate);
            this.listener = listener;
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            listener.onSuccess();
            delegate.close();
        }
    }

}
