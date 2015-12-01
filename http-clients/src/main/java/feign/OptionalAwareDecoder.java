/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
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

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Decorates a Feign {@link Decoder} such that it returns {@link Optional#absent} when observing an HTTP 204 error code
 * for a method with {@link Type} {@link Optional}. Propagates the exception returned by the given {@link ErrorDecoder}
 * in case the response is 204 for a non-Optional method.
 */
public final class OptionalAwareDecoder implements Decoder {

    private final Decoder delegate;
    private final ErrorDecoder errorDecoder;

    public OptionalAwareDecoder(Decoder delegate, ErrorDecoder errorDecoder) {
        this.delegate = delegate;
        this.errorDecoder = errorDecoder;
    }

    @Override
    public Object decode(Response response, Type type) throws IOException, FeignException {
        if (response.status() == 204) {
            if (Types.getRawType(type).equals(Optional.class)) {
                return Optional.absent();
            } else {
                throw Throwables.propagate(errorDecoder.decode(null, response));
            }
        } else {
            return delegate.decode(response, type);
        }
    }
}
