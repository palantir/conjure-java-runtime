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

package com.palantir.test;

import com.google.common.base.Optional;
import com.palantir.config.service.ServiceConfiguration;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.tokens.auth.BearerToken;
import org.junit.Test;

public final class ImmutablesBuilderTests {

    @Test
    public void testServiceConfiguration() {
        // works because "build()" is defined to return ServiceConfiguration
        Optional<BearerToken> bearerTokenOptional = ServiceConfiguration.builder().build().apiToken();

        // compiles -- although "build()" returns ImmutableSslConfiguration (which is not accessible/visible from
        // this package), we store into SslConfiguration
        SslConfiguration sslConfig = SslConfiguration.builder().build();
        Optional<String> stringOptional = sslConfig.keyStoreKeyAlias();

        // functionally equivalent, but does not compile:
        // "error: keyStoreKeyAlias() in ImmutableSslConfiguration is defined in an inaccessible class or interface"
        stringOptional = SslConfiguration.builder().build().keyStoreKeyAlias();
    }

}
