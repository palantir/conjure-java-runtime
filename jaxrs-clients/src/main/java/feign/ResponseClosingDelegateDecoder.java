/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * {@link ResponseClosingDelegateDecoder} closes all {@link Response} instances.
 * This is only necessary when <pre>doNotCloseAfterDecode</pre> has been set
 * on the feign builder.
 */
public final class ResponseClosingDelegateDecoder implements Decoder {
    private final Decoder delegate;

    public ResponseClosingDelegateDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        try {
            return delegate.decode(response, type);
        } finally {
            response.close();
        }
    }
}
