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
import com.palantir.conjure.verification.client.PositiveAndNegativeTestCases;
import com.palantir.conjure.verification.client.VerificationClientRequest;
import com.palantir.conjure.verification.client.VerificationClientService;
import com.palantir.undertest.UndertowServerExtension;
import com.palantir.verification.server.undertest.ServerUnderTestApplication;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

public class AutoDeserializeTest {

    @RegisterExtension
    public static final UndertowServerExtension undertow = ServerUnderTestApplication.createUndertow();

    @RegisterExtension
    public static final VerificationClientRule verificationClientRule = new VerificationClientRule();

    private static final VerificationClientService verificationService =
            VerificationClients.verificationClientService(verificationClientRule);

    public static final class Parameters implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext _context) {
            return Cases.TEST_CASES.getAutoDeserialize().entrySet().stream().flatMap(testCase -> {
                EndpointName endpointName = testCase.getKey();
                PositiveAndNegativeTestCases positiveAndNegativeTestCases = testCase.getValue();

                int positiveSize = positiveAndNegativeTestCases.getPositive().size();
                int negativeSize = positiveAndNegativeTestCases.getNegative().size();

                return Stream.concat(
                        IntStream.range(0, positiveSize)
                                .mapToObj(i -> Arguments.of(
                                        endpointName,
                                        i,
                                        true,
                                        positiveAndNegativeTestCases
                                                .getPositive()
                                                .get(i))),
                        IntStream.range(0, negativeSize)
                                .mapToObj(i -> Arguments.of(
                                        endpointName,
                                        positiveSize + i,
                                        false,
                                        positiveAndNegativeTestCases
                                                .getNegative()
                                                .get(i))));
            });
        }
    }

    @ParameterizedTest(name = "{0}({3}) -> should succeed {2} with client {4}")
    @ArgumentsSource(Parameters.class)
    public void runTestCase(EndpointName endpointName, int index, boolean shouldSucceed, String jsonString) {
        boolean shouldIgnore = Cases.shouldIgnore(endpointName, jsonString);
        System.out.printf(
                "[%s%s test case %s]: %s(%s), expected client to %s%n",
                shouldIgnore ? "ignored " : "",
                shouldSucceed ? "positive" : "negative",
                index,
                endpointName,
                jsonString,
                shouldSucceed ? "succeed" : "fail");

        Optional<Error> expectationFailure =
                shouldSucceed ? expectSuccess(endpointName, index) : expectFailure(endpointName, index);

        if (shouldIgnore) {
            assertThat(expectationFailure)
                    .describedAs(
                            "The test passed but the test case was ignored - remove this from ignored-test-cases.yml")
                    .isNotEmpty();
        }

        Assumptions.assumeFalse(shouldIgnore);

        if (expectationFailure.isPresent()) {
            throw expectationFailure.get();
        }
    }

    private Optional<Error> expectSuccess(EndpointName endpointName, int index) {
        try {
            verificationService.runTestCase(VerificationClientRequest.builder()
                    .endpointName(endpointName)
                    .testCase(index)
                    .baseUrl(String.format("http://localhost:%d/test/api", undertow.getLocalPort()))
                    .build());
            return Optional.empty();
        } catch (RemoteException e) {
            return Optional.of(new AssertionError("Expected call to succeed, but caught exception", e));
        }
    }

    private Optional<Error> expectFailure(EndpointName endpointName, int index) {
        try {
            verificationService.runTestCase(VerificationClientRequest.builder()
                    .endpointName(endpointName)
                    .testCase(index)
                    .baseUrl(String.format("http://localhost:%d/test/api", undertow.getLocalPort()))
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
