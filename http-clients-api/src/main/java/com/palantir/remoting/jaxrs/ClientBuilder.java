/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.jaxrs;

import java.util.Arrays;
import java.util.List;

public abstract class ClientBuilder {
    public abstract <T> T build(Class<T> serviceClass, String userAgent, List<String> uris);

    public final <T> T build(Class<T> serviceClass, String userAgent, String... uris) {
        return build(serviceClass, userAgent, Arrays.asList(uris));
    }
}
