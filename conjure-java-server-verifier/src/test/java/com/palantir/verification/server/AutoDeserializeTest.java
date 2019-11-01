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

import static org.assertj.core.api.Assertions.assertThat;

import com.palantir.conjure.java.api.errors.RemoteException;
import com.palantir.conjure.verification.client.EndpointName;
import com.palantir.conjure.verification.client.VerificationClientRequest;
import com.palantir.conjure.verification.client.VerificationClientService;
import com.palantir.verification.server.undertest.ServerUnderTestApplication;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.assertj.core.api.Assertions;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AutoDeserializeTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> serverUnderTestRule =
            new DropwizardAppRule<>(ServerUnderTestApplication.class, "src/test/resources/config.yml");

    @ClassRule public static final VerificationClientRule verificationClientRule = new VerificationClientRule();
    private static final VerificationClientService verificationService =
            VerificationClients.verificationClientService(verificationClientRule);

    @Parameterized.Parameter(0)
    public EndpointName endpointName;

    @Parameterized.Parameter(1)
    public int index;

    @Parameterized.Parameter(2)
    public boolean shouldSucceed;

    @Parameterized.Parameter(3)
    public String jsonString;

    @Parameterized.Parameters(name = "{0}({3}) -> should succeed {2}")
    public static Collection<Object[]> data() {
        List<Object[]> objects = new ArrayList<>();
        Cases.TEST_CASES.getAutoDeserialize().forEach((endpointName, positiveAndNegativeTestCases) -> {
            int positiveSize = positiveAndNegativeTestCases.getPositive().size();
            int negativeSize = positiveAndNegativeTestCases.getNegative().size();

            IntStream.range(0, positiveSize).forEach(i -> objects.add(
                    new Object[] {endpointName, i, true, positiveAndNegativeTestCases.getPositive().get(i)}));

            IntStream.range(0, negativeSize).forEach(i -> objects.add(new Object[] {
                endpointName, positiveSize + i, false, positiveAndNegativeTestCases.getNegative().get(i)
            }));
        });
        return objects;
    }

    @Test
    public void runTestCase() throws Exception {
        boolean shouldIgnore = Cases.shouldIgnore(endpointName, jsonString);
        System.out.println(String.format(
                "[%s%s test case %s]: %s(%s), expected client to %s",
                shouldIgnore ? "ignored " : "",
                shouldSucceed ? "positive" : "negative",
                index,
                endpointName,
                jsonString,
                shouldSucceed ? "succeed" : "fail"));

        Optional<Error> expectationFailure = shouldSucceed ? expectSuccess() : expectFailure();

        if (shouldIgnore) {
            assertThat(expectationFailure)
                    .describedAs(
                            "The test passed but the test case was ignored - remove this from ignored-test-cases.yml")
                    .isNotEmpty();
        }

        Assume.assumeFalse(shouldIgnore);

        if (expectationFailure.isPresent()) {
            throw expectationFailure.get();
        }
    }

    private Optional<Error> expectSuccess() throws Exception {
        try {
            verificationService.runTestCase(VerificationClientRequest.builder()
                    .endpointName(endpointName)
                    .testCase(index)
                    .baseUrl(String.format("http://localhost:%d/test/api", serverUnderTestRule.getLocalPort()))
                    .build());
            return Optional.empty();
        } catch (RemoteException e) {
            return Optional.of(new AssertionError("Expected call to succeed, but caught exception", e));
        }
    }

    private Optional<Error> expectFailure() {
        try {
            verificationService.runTestCase(VerificationClientRequest.builder()
                    .endpointName(endpointName)
                    .testCase(index)
                    .baseUrl(String.format("http://localhost:%d/test/api", serverUnderTestRule.getLocalPort()))
                    .build());
            return Optional.of(new AssertionError("Result should have caused an exception"));
        } catch (RemoteException e) {
            // It's not an expected failure to get a 404 or 403 back
            Assertions.assertThat(e.getStatus()).isNotEqualTo(403);
            Assertions.assertThat(e.getStatus()).isNotEqualTo(404);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
