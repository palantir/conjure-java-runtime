/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.palantir.remoting2.jaxrs.feignimpl.HeaderAccessUtils;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

public final class CborDelegateEncoder implements Encoder {

    public static final String MIME_TYPE = "application/cbor";

    private final ObjectMapper cborObjectMapper;
    private final Encoder delegate;

    public CborDelegateEncoder(ObjectMapper cborObjectMapper, Encoder delegate) {
        this.cborObjectMapper = cborObjectMapper;
        this.delegate = delegate;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        Collection<String> contentTypes =
                HeaderAccessUtils.caseInsensitiveGet(template.headers(), HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }

        if (!contentTypes.contains(MIME_TYPE)) {
            delegate.encode(object, bodyType, template);
            return;
        }

        try {
            JavaType javaType = cborObjectMapper.getTypeFactory().constructType(bodyType);
            template.body(cborObjectMapper.writerFor(javaType).writeValueAsBytes(object), StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

}
