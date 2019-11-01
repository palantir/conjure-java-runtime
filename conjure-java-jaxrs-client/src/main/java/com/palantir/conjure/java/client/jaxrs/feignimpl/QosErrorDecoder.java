/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import com.palantir.conjure.java.QosExceptionResponseMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

public final class QosErrorDecoder implements ErrorDecoder {
    private final ErrorDecoder delegate;

    public QosErrorDecoder(ErrorDecoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        Optional<Exception> exception = QosExceptionResponseMapper.mapResponseCodeHeaderStream(
                        response.status(), header -> Optional.ofNullable(response.headers().get(header))
                                .map(Collection::stream)
                                .orElseGet(Stream::empty))
                .map(Function.identity());
        return exception.orElseGet(() -> delegate.decode(methodKey, response));
    }
}
