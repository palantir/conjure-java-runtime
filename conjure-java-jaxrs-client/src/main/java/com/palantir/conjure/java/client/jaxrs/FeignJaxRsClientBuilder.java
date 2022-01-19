/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.palantir.conjure.java.client.config.ClientConfiguration;
import com.palantir.conjure.java.serialization.ObjectMappers;

public final class FeignJaxRsClientBuilder extends AbstractFeignJaxRsClientBuilder {

    static final JsonMapper JSON_MAPPER = ObjectMappers.newClientJsonMapper();
    static final CBORMapper CBOR_MAPPER = ObjectMappers.newClientCborMapper();

    FeignJaxRsClientBuilder(ClientConfiguration config) {
        super(config);
    }

    @Override
    protected ObjectMapper getObjectMapper() {
        return JSON_MAPPER;
    }

    @Override
    protected ObjectMapper getCborObjectMapper() {
        return CBOR_MAPPER;
    }
}
