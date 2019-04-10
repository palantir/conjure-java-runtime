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

import com.palantir.conjure.java.client.jaxrs.feignimpl.InputStreamDelegateEncoder;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;

/**
 * Use {@link InputStreamDelegateEncoder}.
 * @deprecated Use {@link InputStreamDelegateEncoder}.
 */
@Deprecated
public final class ConjureInputStreamDelegateEncoder implements Encoder {
    private final Encoder delegate;

    public ConjureInputStreamDelegateEncoder(Encoder delegate) {
        this.delegate = new InputStreamDelegateEncoder(delegate);
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        delegate.encode(object, bodyType, template);
    }
}
