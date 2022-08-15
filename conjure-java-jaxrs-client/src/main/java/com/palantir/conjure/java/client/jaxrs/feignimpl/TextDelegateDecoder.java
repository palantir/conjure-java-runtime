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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Delegates to a {@link StringDecoder} if the response has a Content-Type of text/plain, or falls back to the given
 * delegate otherwise.
 */
public final class TextDelegateDecoder implements Decoder {
    private static final Decoder stringDecoder = new StringDecoder();

    private final Decoder delegate;

    public TextDelegateDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        Collection<String> contentTypes =
                HeaderAccessUtils.caseInsensitiveGet(response.headers(), HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }
        // In the case of multiple content types, or an unknown content type, we'll use the delegate instead.
        if (contentTypes.size() == 1
                && Iterables.getOnlyElement(contentTypes, "").startsWith("text/plain")) {
            Object decoded = stringDecoder.decode(response, type);
            if (decoded == null) {
                return "";
            }
            return decoded;
        }

        return delegate.decode(response, type);
    }
}
