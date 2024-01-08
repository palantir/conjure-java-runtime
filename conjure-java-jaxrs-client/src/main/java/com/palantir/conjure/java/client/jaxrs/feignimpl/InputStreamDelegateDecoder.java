/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import com.codahale.metrics.Meter;
import com.palantir.conjure.java.client.jaxrs.feignimpl.FeignClientMetrics.DangerousBuffering_Direction;
import com.palantir.logsafe.Safe;
import com.palantir.tritium.metrics.registry.SharedTaggedMetricRegistries;
import feign.FeignException;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/** If the return type is InputStream, return it, otherwise delegate to provided decoder. */
public final class InputStreamDelegateDecoder implements Decoder {
    private final Decoder delegate;
    private final Meter dangerousBufferingMeter;

    public InputStreamDelegateDecoder(Decoder delegate) {
        this("unknown", delegate);
    }

    @SuppressWarnings("deprecation") // No access to a TaggedMetricRegistry without breaking API
    public InputStreamDelegateDecoder(@Safe String clientNameForLogging, Decoder delegate) {
        this.delegate = delegate;
        this.dangerousBufferingMeter = FeignClientMetrics.of(SharedTaggedMetricRegistries.getSingleton())
                .dangerousBuffering()
                .client(clientNameForLogging)
                .direction(DangerousBuffering_Direction.RESPONSE)
                .build();
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (type.equals(InputStream.class)) {
            byte[] body =
                    response.body() != null ? Util.toByteArray(response.body().asInputStream()) : new byte[0];
            dangerousBufferingMeter.mark(Math.max(1, body.length));
            return new ByteArrayInputStream(body);
        } else {
            return delegate.decode(response, type);
        }
    }
}
