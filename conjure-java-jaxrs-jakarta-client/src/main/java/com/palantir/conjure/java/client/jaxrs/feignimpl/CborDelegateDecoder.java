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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import feign.FeignException;
import feign.Response;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Currently this checks the Content-Type of the response on every request.
 *
 * <p>In the cases where we know the Content-Type of the response at compile time, i.e. when the only Accepts header is
 * application/cbor, this is unnecessary work.
 *
 * <p>Ideally we'll codegen a client which handles the content-type switching where necessary (multiple possible
 * response Content-Types from the server) and does not do the checking where this is known at compile time.
 */
public final class CborDelegateDecoder implements Decoder {

    private final ObjectMapper cborMapper;
    private final Decoder delegate;

    public CborDelegateDecoder(ObjectMapper cborMapper, Decoder delegate) {
        this.cborMapper = cborMapper;
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        Collection<String> contentTypes =
                HeaderAccessUtils.caseInsensitiveGet(response.headers(), HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }

        if (contentTypes.size() == 1
                && Iterables.getOnlyElement(contentTypes, "").startsWith(CborDelegateEncoder.MIME_TYPE)) {
            // some sillyness to test whether the input stram is empty
            // if it's empty, we want to return null rather than having jackson throw
            int pushbackBufferSize = 1;
            PushbackInputStream pushbackInputStream =
                    new PushbackInputStream(response.body().asInputStream(), pushbackBufferSize);
            int firstByte = pushbackInputStream.read();
            if (firstByte == -1) {
                return null; // we don't have any data in the stream
            }
            // put the byte back
            pushbackInputStream.unread(firstByte);

            return cborMapper.readValue(pushbackInputStream, cborMapper.constructType(type));

        } else {
            return delegate.decode(response, type);
        }
    }
}
