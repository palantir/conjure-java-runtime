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

package com.palantir.verification;

import com.palantir.conjure.java.client.jaxrs.JaxRsClient;
import com.palantir.conjure.java.client.retrofit2.Retrofit2Client;
import com.palantir.conjure.java.dialogue.serde.DefaultConjureRuntime;
import com.palantir.conjure.java.okhttp.HostMetricsRegistry;
import com.palantir.conjure.verification.server.AutoDeserializeConfirmService;
import com.palantir.conjure.verification.server.AutoDeserializeService;
import com.palantir.conjure.verification.server.AutoDeserializeServiceRetrofit;
import com.palantir.conjure.verification.server.SingleHeaderService;
import com.palantir.conjure.verification.server.SinglePathParamService;
import com.palantir.conjure.verification.server.SingleQueryParamService;
import com.palantir.dialogue.Channel;
import com.palantir.dialogue.hc4.ApacheHttpClientChannels;

public final class VerificationClients {
    private VerificationClients() {}

    public static AutoDeserializeService autoDeserializeServiceJersey(VerificationServerRule server) {
        return JaxRsClient.create(
                AutoDeserializeService.class,
                server.getClientConfiguration().userAgent().orElseThrow(IllegalArgumentException::new),
                new HostMetricsRegistry(),
                server.getClientConfiguration());
    }

    public static AutoDeserializeService autoDeserializeServiceJerseyDialogue(VerificationServerRule server) {
        return jaxrsDialogue(AutoDeserializeService.class, server);
    }

    public static AutoDeserializeServiceRetrofit autoDeserializeServiceRetrofit(VerificationServerRule server) {
        return Retrofit2Client.create(
                AutoDeserializeServiceRetrofit.class,
                server.getClientConfiguration().userAgent().orElseThrow(IllegalArgumentException::new),
                new HostMetricsRegistry(),
                server.getClientConfiguration());
    }

    public static AutoDeserializeConfirmService confirmService(VerificationServerRule server) {
        return JaxRsClient.create(
                AutoDeserializeConfirmService.class,
                server.getClientConfiguration().userAgent().orElseThrow(IllegalArgumentException::new),
                new HostMetricsRegistry(),
                server.getClientConfiguration());
    }

    public static SinglePathParamService singlePathParamService(VerificationServerRule server) {
        return JaxRsClient.create(
                SinglePathParamService.class,
                server.getClientConfiguration().userAgent().orElseThrow(IllegalArgumentException::new),
                new HostMetricsRegistry(),
                server.getClientConfiguration());
    }

    public static SinglePathParamService singlePathParamServiceDialogue(VerificationServerRule server) {
        return jaxrsDialogue(SinglePathParamService.class, server);
    }

    public static SingleHeaderService singleHeaderService(VerificationServerRule server) {
        return JaxRsClient.create(
                SingleHeaderService.class,
                server.getClientConfiguration().userAgent().orElseThrow(IllegalArgumentException::new),
                new HostMetricsRegistry(),
                server.getClientConfiguration());
    }

    public static SingleHeaderService singleHeaderServiceDialogue(VerificationServerRule server) {
        return jaxrsDialogue(SingleHeaderService.class, server);
    }

    public static SingleQueryParamService singleQueryParamService(VerificationServerRule server) {
        return JaxRsClient.create(
                SingleQueryParamService.class,
                server.getClientConfiguration().userAgent().orElseThrow(IllegalArgumentException::new),
                new HostMetricsRegistry(),
                server.getClientConfiguration());
    }

    public static SingleQueryParamService singleQueryParamServiceDialogue(VerificationServerRule server) {
        return jaxrsDialogue(SingleQueryParamService.class, server);
    }

    private static <T> T jaxrsDialogue(Class<T> service, VerificationServerRule server) {
        Channel channel = ApacheHttpClientChannels.create(server.getClientConfiguration());
        return JaxRsClient.create(
                service, channel, DefaultConjureRuntime.builder().build());
    }
}
