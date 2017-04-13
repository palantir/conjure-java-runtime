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
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
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
            InputStream inputStream = response.body().asInputStream();
            if (!inputStream.markSupported()) {
                inputStream = new BufferedInputStream(inputStream, 1);
            }

            // Read the first byte to see if we have any data
            inputStream.mark(1);
            if (inputStream.read() == -1) {
                return null; // Eagerly returning null avoids "No content to map due to end-of-input"
            }

            inputStream.reset();
            return cborObjectMapper.readValue(inputStream, cborObjectMapper.constructType(type));

        } else {
            return delegate.decode(response, type);
        }
    }

}
