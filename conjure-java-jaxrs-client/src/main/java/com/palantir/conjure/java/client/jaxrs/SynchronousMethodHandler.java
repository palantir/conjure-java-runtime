/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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
package com.palantir.conjure.java.client.jaxrs;

final class SynchronousMethodHandler implements MethodHandler {

    private static final long MAX_RESPONSE_BUFFER_SIZE = 8192L;

    private final MethodMetadata metadata;
    private final Client client;
    private final RequestTemplate.Factory buildTemplateFromArgs;
    private final Decoder decoder;
    private final ErrorDecoder errorDecoder;

    private SynchronousMethodHandler(
            Client client,
            MethodMetadata metadata,
            RequestTemplate.Factory buildTemplateFromArgs,
            Decoder decoder,
            ErrorDecoder errorDecoder) {
        this.client = Util.checkNotNull(client, "client");
        this.metadata = Util.checkNotNull(metadata, "metadata");
        this.buildTemplateFromArgs = Util.checkNotNull(buildTemplateFromArgs, "metadata");
        this.errorDecoder = Util.checkNotNull(errorDecoder, "errorDecoder");
        this.decoder = Util.checkNotNull(decoder, "decoder");
    }

    @Override
    public Object invoke(Object[] argv) throws Throwable {
        RequestTemplate template = buildTemplateFromArgs.create(argv);
        return executeAndDecode(template);
    }

    Object executeAndDecode(RequestTemplate template) throws Exception {
        Request request = template.request();

        Response response = client.execute(request);

        boolean shouldClose = true;
        try {
            if (Response.class == metadata.returnType()) {
                if (response.body() == null) {
                    return response;
                }
                if (response.body().length() == null || response.body().length() > MAX_RESPONSE_BUFFER_SIZE) {
                    shouldClose = false;
                    return response;
                }
                // Ensure the response body is disconnected
                byte[] bodyData = Util.toByteArray(response.body().asInputStream());
                return Response.create(response.status(), response.headers(), bodyData);
            }
            if (response.status() >= 200 && response.status() < 300) {
                if (void.class == metadata.returnType()) {
                    return null;
                } else {
                    return decoder.decode(response, metadata.returnType());
                }
            } else {
                throw errorDecoder.decode(metadata.configKey(), response);
            }
        } finally {
            if (shouldClose) {
                Util.ensureClosed(response.body());
            }
        }
    }

    static class Factory {

        private final Client client;

        Factory(Client client) {
            this.client = Util.checkNotNull(client, "client");
        }

        public MethodHandler create(
                MethodMetadata md,
                RequestTemplate.Factory buildTemplateFromArgs,
                Decoder decoder,
                ErrorDecoder errorDecoder) {
            return new SynchronousMethodHandler(client, md, buildTemplateFromArgs, decoder, errorDecoder);
        }
    }
}
