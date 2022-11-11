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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.conjure.verification.server.EndpointName;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.commons.lang3.ClassUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SingleParamServicesTest {

    @RegisterExtension
    public static final VerificationServerRule server = new VerificationServerRule();

    private static final Logger log = LoggerFactory.getLogger(SingleParamServicesTest.class);
    private static final JsonMapper jsonMapper = ObjectMappers.newClientJsonMapper();
    private static final ImmutableMap<String, Object> servicesMapsJaxrs = ImmutableMap.of(
            "singlePathParamService",
            VerificationClients.singlePathParamService(server),
            "singleHeaderService",
            VerificationClients.singleHeaderService(server),
            "singleQueryParamService",
            VerificationClients.singleQueryParamService(server));

    private static Collection<Arguments> data() {
        List<Arguments> objects = new ArrayList<>();
        Cases.TEST_CASES.getSingleHeaderService().forEach((endpointName, singleHeaderTestCases) -> {
            int size = singleHeaderTestCases.size();
            IntStream.range(0, size)
                    .forEach(i -> objects.add(
                            Arguments.of("singleHeaderService", endpointName, i, singleHeaderTestCases.get(i))));
        });

        Cases.TEST_CASES.getSinglePathParamService().forEach((endpointName, singleHeaderTestCases) -> {
            int size = singleHeaderTestCases.size();
            IntStream.range(0, size)
                    .forEach(i -> objects.add(
                            Arguments.of("singlePathParamService", endpointName, i, singleHeaderTestCases.get(i))));
        });

        Cases.TEST_CASES.getSingleQueryParamService().forEach((endpointName, singleQueryTestCases) -> {
            int size = singleQueryTestCases.size();
            IntStream.range(0, size)
                    .forEach(i -> objects.add(
                            Arguments.of("singleQueryParamService", endpointName, i, singleQueryTestCases.get(i))));
        });

        return objects;
    }

    @ParameterizedTest(name = "{0}/{1}({3})")
    @MethodSource("data")
    public void runTestCaseJaxrs(String serviceName, EndpointName endpointName, int index, String jsonString)
            throws Exception {
        Assumptions.assumeFalse(Cases.shouldIgnoreJersey(endpointName, jsonString));

        log.info("Invoking {} {}({})", serviceName, endpointName, jsonString);

        Object service = servicesMapsJaxrs.get(serviceName);
        assertThat(service).isNotNull();
        for (Method method : service.getClass().getMethods()) {
            String name = method.getName();

            if (method.getParameterCount() == 1) {
                // conjure-java generates `default` methods for optional query params, we don't want to call these
                continue;
            }

            if (endpointName.get().equals(name)) {
                try {
                    // HACKHACK, index parameter order is different for different services.
                    if ("singleHeaderService".equals(serviceName)) {
                        Type type = method.getGenericParameterTypes()[0];
                        Class<?> cls = ClassUtils.getClass(type.getTypeName());
                        method.invoke(service, jsonMapper.readValue(jsonString, cls), index);
                    } else {
                        Type type = method.getGenericParameterTypes()[1];
                        Class<?> cls = ClassUtils.getClass(type.getTypeName());
                        method.invoke(service, index, jsonMapper.readValue(jsonString, cls));
                    }

                    log.info("Successfully post param to endpoint {} and index {}", endpointName, index);
                } catch (RemoteException e) {
                    log.error("Caught exception with params: {}", e.getError().parameters(), e);
                    throw e;
                }
            }
        }
    }
}
