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

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.palantir.conjure.verification.client.EndpointName;
import com.palantir.conjure.verification.client.IgnoredServerTestCases;
import com.palantir.conjure.verification.client.IgnoredTestCases;
import com.palantir.conjure.verification.client.ServerTestCases;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import javax.annotation.Nullable;

public final class Cases {

    public static final ServerTestCases TEST_CASES = deserializeTestCases(new File("build/test-cases/test-cases.json"));
    private static final IgnoredServerTestCases IGNORED_TEST_CASES =
            deserializeIgnoredServerTestCases(new File("src/test/resources/ignored-test-cases.yml"));

    private Cases() {}

    private static ServerTestCases deserializeTestCases(File file) {
        try {
            return JsonMapper.builder()
                    .addModule(new Jdk8Module())
                    .build()
                    .readValue(file, com.palantir.conjure.verification.client.TestCases.class)
                    .getServer();
        } catch (IOException e) {
            throw new RuntimeException(
                    String.format("Unable to read %s, you may need to run ./gradlew copyTestCases", file), e);
        }
    }

    private static IgnoredServerTestCases deserializeIgnoredServerTestCases(File file) {
        try {
            return YAMLMapper.builder()
                    .addModule(new Jdk8Module())
                    .build()
                    .readValue(file, IgnoredTestCases.class)
                    .getServer();
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read %s", file), e);
        }
    }

    public static boolean shouldIgnore(EndpointName endpointName, String json) {
        return setContains(IGNORED_TEST_CASES.getAutoDeserialize().get(endpointName), json)
                || setContains(IGNORED_TEST_CASES.getSingleHeaderService().get(endpointName), json)
                || setContains(IGNORED_TEST_CASES.getSinglePathParamService().get(endpointName), json)
                || setContains(IGNORED_TEST_CASES.getSingleQueryParamService().get(endpointName), json);
    }

    private static boolean setContains(@Nullable Set<String> set, String item) {
        if (set == null) {
            return false;
        }

        return set.contains(item);
    }
}
