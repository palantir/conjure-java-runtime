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
import feign.RequestTemplate;
import feign.Util;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/** If the body type is an InputStream, write it into the body, otherwise pass to delegate. */
public final class InputStreamDelegateEncoder implements Encoder {
    private final Encoder delegate;
    private final Meter dangerousBufferingMeter;

    public InputStreamDelegateEncoder(Encoder delegate) {
        this("unknown", delegate);
    }

    @SuppressWarnings("deprecation") // No access to a TaggedMetricRegistry without breaking API
    public InputStreamDelegateEncoder(@Safe String clientNameForLogging, Encoder delegate) {
        this.delegate = delegate;
        this.dangerousBufferingMeter = FeignClientMetrics.of(SharedTaggedMetricRegistries.getSingleton())
                .dangerousBuffering()
                .client(clientNameForLogging)
                .direction(DangerousBuffering_Direction.REQUEST)
                .build();
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        if (bodyType.equals(InputStream.class)) {
            try {
                byte[] bytes = Util.toByteArray((InputStream) object);
                dangerousBufferingMeter.mark(Math.max(1, bytes.length));
                template.body(bytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            delegate.encode(object, bodyType, template);
        }
    }
}
