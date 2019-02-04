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

import feign.codec.Decoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

/**
 * If the return type is InputStream, return it, otherwise delegate to provided decoder.
 */
public final class ConjureInputStreamDelegateDecoder implements Decoder {
    private final Decoder delegate;

    public ConjureInputStreamDelegateDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (type.equals(InputStream.class)) {
            byte[] body = response.body() != null ? Util.toByteArray(response.body().asInputStream()) : new byte[0];
            return new ByteArrayInputStream(body);
        } else {
            return delegate.decode(response, type);
        }
    }
}
