/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Decodes the response as a string if the response has a Content-Type of text/plain, or falls back to the given
 * delegate otherwise.
 */
final class TextDelegateDecoder implements Decoder {

    private final Decoder delegate;

    TextDelegateDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException {
        Collection<String> contentTypes =
                HeaderAccessUtils.caseInsensitiveGet(response.headers(), HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }
        // In the case of multiple content types, or an unknown content type, we'll use the delegate instead.
        if (contentTypes.size() == 1
                && Iterables.getOnlyElement(contentTypes, "").startsWith("text/plain")) {
            Response.Body body = response.body();
            if (body == null) {
                return "";
            }
            if (String.class.equals(type)) {
                return Util.toString(body.asReader());
            }
            throw new SafeRuntimeException("Type is not supported by this encoder", SafeArg.of("type", type));
        }

        return delegate.decode(response, type);
    }
}
