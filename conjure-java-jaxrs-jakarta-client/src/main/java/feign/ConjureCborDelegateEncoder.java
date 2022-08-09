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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.conjure.java.client.jaxrs.feignimpl.CborDelegateEncoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;

/**
 * Use {@link CborDelegateEncoder}.
 *
 * @deprecated Use {@link CborDelegateEncoder}.
 */
@Deprecated
public final class ConjureCborDelegateEncoder implements Encoder {

    @SuppressWarnings("unused") // public API
    public static final String MIME_TYPE = CborDelegateEncoder.MIME_TYPE;

    private final Encoder delegate;

    public ConjureCborDelegateEncoder(ObjectMapper cborMapper, Encoder delegate) {
        this.delegate = new CborDelegateEncoder(cborMapper, delegate);
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        delegate.encode(object, bodyType, template);
    }
}
