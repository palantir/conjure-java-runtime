/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package feign;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.net.HttpHeaders;
import com.palantir.conjure.java.client.jaxrs.feignimpl.HeaderAccessUtils;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * An encoder which checks the Content-Type headers for the presence of
 * application/cbor. If present, encodes the request body as cbor and otherwise
 * delegates the encoding.
 *
 * It's silly that we must do this check every time, given the request content
 * type is fixed at compile time.
 *
 * In the future we will likely codegen the client and thus remove the need for
 * scanning the headers on every request.
 */
public final class ConjureCborDelegateEncoder implements Encoder {

    public static final String MIME_TYPE = "application/cbor";

    private final ObjectMapper cborObjectMapper;
    private final Encoder delegate;

    public ConjureCborDelegateEncoder(ObjectMapper cborObjectMapper, Encoder delegate) {
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
