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
import feign.RequestTemplate;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Delegates to a {@link feign.codec.Encoder.Default} if the response has a Content-Type of text/plain, or falls back to
 * the given delegate otherwise.
 */
public final class TextDelegateEncoder implements Encoder {
    private static final Encoder defaultEncoder = new Encoder.Default();

    private final Encoder delegate;

    public TextDelegateEncoder(Encoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        Collection<String> contentTypes =
                HeaderAccessUtils.caseInsensitiveGet(template.headers(), HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }

        // In the case of multiple content types, or an unknown content type, we'll use the delegate instead.
        if (contentTypes.size() == 1
                && Iterables.getOnlyElement(contentTypes, "").equals("text/plain")) {
            defaultEncoder.encode(object, bodyType, template);
        } else {
            delegate.encode(object, bodyType, template);
        }
    }
}
