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

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.palantir.conjure.verification.server.AutoDeserializeConfirmService;
import com.palantir.conjure.verification.server.AutoDeserializeService;
import com.palantir.conjure.verification.server.AutoDeserializeServiceRetrofit;
import com.palantir.conjure.verification.server.EndpointName;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoDeserializeTest {

    @RegisterExtension
    public static final VerificationServerRule server = new VerificationServerRule();

    private static final Logger log = LoggerFactory.getLogger(AutoDeserializeTest.class);
    private static final AutoDeserializeService testServiceJersey =
            VerificationClients.autoDeserializeServiceJersey(server);
    private static final AutoDeserializeServiceRetrofit testServiceRetrofit =
            VerificationClients.autoDeserializeServiceRetrofit(server);
    private static final AutoDeserializeConfirmService confirmService = VerificationClients.confirmService(server);

    private static Collection<Arguments> data() {
        List<Arguments> objects = new ArrayList<>();
        Cases.TEST_CASES.getAutoDeserialize().forEach((endpointName, positiveAndNegativeTestCases) -> {
            int positiveSize = positiveAndNegativeTestCases.getPositive().size();
            int negativeSize = positiveAndNegativeTestCases.getNegative().size();

            IntStream.range(0, positiveSize)
                    .forEach(i -> objects.add(Arguments.of(
                            endpointName,
                            i,
                            true,
                            positiveAndNegativeTestCases.getPositive().get(i))));

            IntStream.range(0, negativeSize)
                    .forEach(i -> objects.add(Arguments.of(
                            endpointName,
                            positiveSize + i,
                            false,
                            positiveAndNegativeTestCases.getNegative().get(i))));
        });
        return objects;
    }

    @ParameterizedTest(name = "{0}({3}) -> should succeed {2}")
    @MethodSource("data")
    @SuppressWarnings("IllegalThrows")
    public void runTestCaseRetrofit(EndpointName endpointName, int index, boolean shouldSucceed, String jsonString)
            throws Error, NoSuchMethodException {
        boolean shouldIgnore = Cases.shouldIgnoreRetrofit(endpointName, jsonString);
        Method method = testServiceRetrofit.getClass().getMethod(endpointName.get(), int.class);
        System.out.printf(
                "[%s%s test case %s]: %s(%s), expected client to %s%n",
                shouldIgnore ? "ignored " : "",
                shouldSucceed ? "positive" : "negative",
                index,
                endpointName,
                jsonString,
                shouldSucceed ? "succeed" : "fail");

        Optional<Error> expectationFailure = shouldSucceed
                ? expectSuccessRetrofit(method, endpointName, index)
                : expectFailureRetrofit(method, index);

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

    @ParameterizedTest(name = "{0}({3}) -> should succeed {2}")
    @MethodSource("data")
    @SuppressWarnings("IllegalThrows")
    public void runTestCaseJersey(EndpointName endpointName, int index, boolean shouldSucceed, String jsonString)
            throws Error, NoSuchMethodException {
        boolean shouldIgnore = Cases.shouldIgnoreJersey(endpointName, jsonString);
        Method method = testServiceJersey.getClass().getMethod(endpointName.get(), int.class);
        System.out.printf(
                "[%s%s test case %s]: %s(%s), expected client to %s%n",
                shouldIgnore ? "ignored " : "",
                shouldSucceed ? "positive" : "negative",
                index,
                endpointName,
                jsonString,
                shouldSucceed ? "succeed" : "fail");

        Optional<Error> expectationFailure =
                shouldSucceed ? expectSuccessJersey(method, endpointName, index) : expectFailureJersey(method, index);

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

    private Optional<Error> expectSuccessRetrofit(Method method, EndpointName endpointName, int index) {
        try {
            Object result = Futures.getUnchecked((ListenableFuture<?>) method.invoke(testServiceRetrofit, index));
            log.info("Received result for endpoint {} and index {}: {}", endpointName, index, result);
            Object clientResponse = result;
            if (result instanceof ResponseBody) {
                ResponseBody body = (ResponseBody) result;
                clientResponse = body.bytes();
            }
            confirmService.confirm(endpointName, index, clientResponse);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new AssertionError("Expected call to succeed, but caught exception", e));
        }
    }

    private Optional<Error> expectFailureRetrofit(Method method, int index) {
        try {
            Object result = Futures.getUnchecked((ListenableFuture<?>) method.invoke(testServiceRetrofit, index));
            return Optional.of(new AssertionError(
                    String.format("Result should have caused an exception but deserialized to: %s", result)));
        } catch (Exception e) {
            return Optional.empty(); // we expected the method to throw, and it did, so this expectation was satisfied
        }
    }

    private Optional<Error> expectSuccessJersey(Method method, EndpointName endpointName, int index) {
        try {
            Object resultFromServer = method.invoke(testServiceJersey, index);
            log.info("Received result for endpoint {} and index {}: {}", endpointName, index, resultFromServer);
            confirmService.confirm(endpointName, index, resultFromServer);
            return Optional.empty();
        } catch (Exception e) {
            return Optional.of(new AssertionError("Expected call to succeed, but caught exception", e));
        }
    }

    private Optional<Error> expectFailureJersey(Method method, int index) {
        try {
            Object result = method.invoke(testServiceJersey, index);
            return Optional.of(new AssertionError(
                    String.format("Result should have caused an exception but deserialized to: %s", result)));
        } catch (Exception e) {
            return Optional.empty(); // we expected the method to throw, and it did, so this expectation was satisfied
        }
    }
}
