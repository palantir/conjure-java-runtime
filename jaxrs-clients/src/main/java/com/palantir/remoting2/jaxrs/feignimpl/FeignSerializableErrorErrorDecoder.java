/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.jaxrs.feignimpl;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting2.errors.SerializableErrorToExceptionConverter;
import feign.Response;
import feign.RetryableException;
import feign.Util;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public enum FeignSerializableErrorErrorDecoder implements ErrorDecoder {
    INSTANCE;

    private static final RetryAfterDecoder RETRY_AFTER_DECODER = new RetryAfterDecoder();

    @Override
    public Exception decode(String methodKey, Response response) {
        Collection<String> contentTypes =
                HeaderAccessUtils.caseInsensitiveGet(response.headers(), HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }

        InputStream body;
        try {
            body = response.body().asInputStream();
        } catch (IOException e) {
            return new RuntimeException("Cannot get input stream from response: " + e.getMessage(), e);
        }
        Exception exception = SerializableErrorToExceptionConverter.getException(
                contentTypes,
                response.status(),
                response.reason(),
                body);

        Collection<String> retryAfter =
                HeaderAccessUtils.caseInsensitiveGet(response.headers(), HttpHeaders.RETRY_AFTER);
        if (retryAfter != null) {
            Date retryAfterDate = RETRY_AFTER_DECODER.apply(Iterables.getFirst(retryAfter, null));
            if (retryAfterDate != null) {
                return new RetryableException(exception.getMessage(), exception, retryAfterDate);
            }
        }

        return exception;
    }

    /**
     * Decodes a {@link feign.Util#RETRY_AFTER} header into an absolute date, if possible. <br> See <a
     * href="https://tools.ietf.org/html/rfc2616#section-14.37">Retry-After format</a>
     */
    static class RetryAfterDecoder {

        static final DateFormat
                RFC822_FORMAT =
                new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        private final DateFormat rfc822Format;

        RetryAfterDecoder() {
            this(RFC822_FORMAT);
        }

        RetryAfterDecoder(DateFormat rfc822Format) {
            this.rfc822Format = Util.checkNotNull(rfc822Format, "rfc822Format");
        }

        protected long currentTimeMillis() {
            return System.currentTimeMillis();
        }

        /**
         * returns a date that corresponds to the first time a request can be retried.
         *
         * @param retryAfter String in <a href="https://tools.ietf.org/html/rfc2616#section-14.37"
         *                   >Retry-After format</a>
         */
        public Date apply(String retryAfter) {
            if (retryAfter == null) {
                return null;
            }
            if (retryAfter.matches("^[0-9]+$")) {
                long deltaMillis = TimeUnit.SECONDS.toMillis(Long.parseLong(retryAfter));
                return new Date(currentTimeMillis() + deltaMillis);
            }
            synchronized (rfc822Format) {
                try {
                    return rfc822Format.parse(retryAfter);
                } catch (ParseException ignored) {
                    return null;
                }
            }
        }
    }
}
