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
import com.palantir.conjure.java.client.jaxrs.feignimpl.CborDelegateDecoder;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Use {@link CborDelegateDecoder}.
 *
 * @deprecated Use {@link CborDelegateDecoder}.
 */
@Deprecated
public final class ConjureCborDelegateDecoder implements Decoder {

    private final CborDelegateDecoder delegate;

    public ConjureCborDelegateDecoder(ObjectMapper cborMapper, Decoder delegate) {
        this.delegate = new CborDelegateDecoder(cborMapper, delegate);
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        return delegate.decode(response, type);
    }
}
