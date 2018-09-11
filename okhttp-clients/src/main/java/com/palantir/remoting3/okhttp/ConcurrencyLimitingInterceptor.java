package com.palantir.remoting3.okhttp;

import static com.google.common.base.Preconditions.checkState;

import com.netflix.concurrency.limits.Limiter;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.AsyncTimeout;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger log = LoggerFactory.getLogger(ConcurrencyLimitingInterceptor.class);
    private final ConcurrencyLimiters limiters = new ConcurrencyLimiters();

    @Override
    public Response intercept(Chain chain) throws IOException {
        ConcurrencyLimitTag tagState = chain.request().tag(ConcurrencyLimitTag.class);
        tagState.invalidate();
        tagState.setListener(limiters.limiter(chain.request()));
        return chain.proceed(chain.request());
    }

    public static Callback wrapCallback(Callback callback) {
        return new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Optional.ofNullable(call.request().tag(ConcurrencyLimitTag.class)).ifPresent(ConcurrencyLimitTag::invalidate);
                callback.onFailure(call, e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Response newResponse =
                        Optional.ofNullable(call.request().tag(ConcurrencyLimitTag.class))
                                .map(t -> wrapResponse(t, response))
                        .orElse(response);
                callback.onResponse(call, newResponse);
            }
        };
    }

    public static Request wrapRequest(Request request) {
        return request.newBuilder().tag(ConcurrencyLimitTag.class, new ConcurrencyLimitTag()).build();
    }

    private static Response wrapResponse(ConcurrencyLimitTag tag, Response response) {
        if (response.body() == null) {
            return response;
        }
        ResponseBody currentBody = response.body();
        ResourceDeallocator deallocator = new ResourceDeallocator(tag);
        ResponseBody newResponseBody =
                ResponseBody.create(currentBody.contentType(), currentBody.contentLength(),
                        new ReleaseConcurrencyLimitBufferedSource(currentBody.source(), tag, deallocator));
        deallocator.timeout(1, TimeUnit.MINUTES);
        deallocator.enter();
        return response.newBuilder()
                .body(newResponseBody)
                .build();
    }

    static final class ConcurrencyLimitTag {
        private Limiter.Listener listener;
        private boolean wasDropped = false;

        private void invalidate() {
            if (listener == null) {
                return;
            }

            if (wasDropped) {
                listener.onDropped();
            } else {
                listener.onIgnore();
            }
            listener = null;
            wasDropped = false;
        }

        private void setListener(Limiter.Listener listener) {
            checkState(listener == null);
            this.listener = listener;
        }

        public void success() {
            listener.onSuccess();
        }

        public void wasDropped() {
            wasDropped = true;
        }
    }

    private static final class ResourceDeallocator extends AsyncTimeout {
        private final ConcurrencyLimitTag tag;

        private ResourceDeallocator(ConcurrencyLimitTag tag) {
            this.tag = tag;
        }

        @Override
        public void timedOut() {
            log.warn("A call appears to have been leaked. We think this is an application bug caused by not properly "
                    + "cleaning up the response object. Make sure you close() it!");
            tag.invalidate();
        }
    }

    private static final class ReleaseConcurrencyLimitBufferedSource extends ForwardingBufferedSource {
        private final BufferedSource delegate;
        private final ConcurrencyLimitTag tag;
        private final ResourceDeallocator deallocator;

        private ReleaseConcurrencyLimitBufferedSource(BufferedSource delegate,
                ConcurrencyLimitTag tag,
                ResourceDeallocator deallocator) {
            super(delegate);
            this.delegate = delegate;
            this.tag = tag;
            this.deallocator = deallocator;
        }

        @Override
        public void close() throws IOException {
            if (deallocator.exit()) {
                log.info("The timeout fired but we have now closed the source. This implies a very long lived "
                        + "call being used properly, which the Conjure devs do not expect.");
            }
            tag.success();
            delegate.close();
        }
    }

}
