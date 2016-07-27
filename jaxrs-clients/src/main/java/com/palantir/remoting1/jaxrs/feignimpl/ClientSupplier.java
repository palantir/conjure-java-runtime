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

package com.palantir.remoting1.jaxrs.feignimpl;

import com.google.common.base.Optional;
import feign.Client;
import javax.net.ssl.SSLSocketFactory;

/**
 * Given an optional {@link javax.net.ssl.SSLSocketFactory} and a user agent, creates and returns a {@link feign.Client
 * Feign client}.
 */
public interface ClientSupplier {
    Client createClient(Optional<SSLSocketFactory> sslSocketFactory, String userAgent);
}
