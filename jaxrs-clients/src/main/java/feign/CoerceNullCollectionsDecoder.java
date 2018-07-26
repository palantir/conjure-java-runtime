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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CoerceNullCollectionsDecoder implements Decoder {
    private final Decoder delegate;

    public CoerceNullCollectionsDecoder(Decoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object decode(Response response, Type type) throws FeignException, IOException {
        Object object = delegate.decode(response, type);
        if ((response.status() == 200 || response.status() == 204) && object == null) {
            Class<?> rawType = Types.getRawType(type);
            if (List.class.isAssignableFrom(rawType)) {
                return Collections.emptyList();
            } else if (Set.class.isAssignableFrom(rawType)) {
                return Collections.emptySet();
            } else if (Map.class.isAssignableFrom(rawType)) {
                return Collections.emptyMap();
            }
        }
        return object;
    }
}
