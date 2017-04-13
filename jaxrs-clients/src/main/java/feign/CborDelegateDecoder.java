/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting2.jaxrs.feignimpl.HeaderAccessUtils;
import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.lang.reflect.Type;
import java.util.Collection;


public final class CborDelegateDecoder implements Decoder {

    private final ObjectMapper cborObjectMapper;
    private final Decoder delegate;

    public CborDelegateDecoder(ObjectMapper cborObjectMapper, Decoder delegate) {
        this.cborObjectMapper = cborObjectMapper;
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {
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
            PushbackInputStream pushbackInputStream = new PushbackInputStream(
                    response.body().asInputStream(), pushbackBufferSize);
            int firstByte = pushbackInputStream.read();
            if (firstByte == -1) {
                return null; // we don't have any data in the stream
            }
            // put the byte back
            pushbackInputStream.unread(firstByte);

            return cborObjectMapper.readValue(pushbackInputStream, cborObjectMapper.constructType(type));

        } else {
            return delegate.decode(response, type);
        }
    }

}
