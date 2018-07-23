/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.jaxrs.feignimpl.HeaderAccessUtils;
import feign.codec.Decoder;
import java.io.IOException;
import java.io.PushbackInputStream;
import java.lang.reflect.Type;
import java.util.Collection;

/**
 * Currently this checks the Content-Type of the response on every request.
 * <p>
 * In the cases where we know the Content-Type of the response at compile time, i.e. when the only Accepts header is
 * application/cbor, this is unnecessary work.
 * <p>
 * Ideally we'll codegen a client which handles the content-type switching where necessary (multiple possible response
 * Content-Types from the server) and does not do the checking where this is known at compile time.
 */
public final class CborDelegateDecoder implements Decoder {

    private final ObjectMapper cborObjectMapper;
    private final Decoder delegate;

    public CborDelegateDecoder(ObjectMapper cborObjectMapper, Decoder delegate) {
        this.cborObjectMapper = cborObjectMapper;
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
