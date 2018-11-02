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

package com.palantir.verification.server;

import com.palantir.conjure.java.api.config.service.UserAgent;
import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.okhttp.NoOpHostEventsSink;
import com.palantir.conjure.verification.client.VerificationClientService;

public final class VerificationClients {
    private VerificationClients() {}

    public static VerificationClientService verificationClientService(VerificationClientRule verificationClientRule) {
        return JaxRsClient.create(
                VerificationClientService.class,
                getUserAgent(),
                NoOpHostEventsSink.INSTANCE,
                verificationClientRule.getClientConfiguration());
    }

    private static UserAgent getUserAgent() {
        return UserAgent.of(UserAgent.Agent.of("test", "develop"));
    }
}
