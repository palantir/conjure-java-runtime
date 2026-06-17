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

package com.palantir.conjure.java.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Stopwatch;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class JacksonWarmupTest {
    static final long EXPECTED_OBJECT_MAPPERS = 8;
    static final long EXPECTED_PAYLOADS = 9;

    @Test
    void runWithoutArgs() {
        Stopwatch timer = Stopwatch.createStarted();
        assertThat(JacksonWarmup.run())
                .isNotZero()
                .isEqualTo(EXPECTED_OBJECT_MAPPERS * JacksonWarmup.ITERATIONS * EXPECTED_PAYLOADS);
        Duration elapsed = timer.elapsed();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5)); // generous to avoid CI flakiness
    }

    @Test
    void runWithObjectMappers() {
        ObjectMapper[] objectMappers = objectMappers().toArray(ObjectMapper[]::new);
        // Per-test mappers so the warmup also exercises mapper-construction overhead, not just steady-state writes.
        Stopwatch timer = Stopwatch.createStarted();
        assertThat(JacksonWarmup.run(objectMappers))
                .isNotZero()
                .isEqualTo(objectMappers.length * JacksonWarmup.ITERATIONS * EXPECTED_PAYLOADS);
        Duration elapsed = timer.elapsed();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5)); // generous to avoid CI flakiness
    }

    @ParameterizedTest
    @MethodSource("objectMappers")
    void runWithObjectMapper(ObjectMapper objectMapper) {
        Stopwatch timer = Stopwatch.createStarted();
        assertThat(JacksonWarmup.run(objectMapper)).isNotZero().isEqualTo(JacksonWarmup.ITERATIONS * EXPECTED_PAYLOADS);
        Duration elapsed = timer.elapsed();
        assertThat(elapsed).isLessThan(Duration.ofSeconds(2)); // generous to avoid CI flakiness
    }

    @Test
    void runNoOpWithMissingModules() {
        assertThat(JacksonWarmup.run(new ObjectMapper()))
                .as("ObjectMapper cannot serialize Java 8 optional type `java.util.Optional<java.lang.String>` by"
                        + " default")
                .isZero();
    }

    static Stream<ObjectMapper> objectMappers() {
        return Stream.of(
                ObjectMappers.newServerObjectMapper(),
                ObjectMappers.newClientObjectMapper(),
                ObjectMappers.newServerCborMapper(),
                ObjectMappers.newClientCborMapper(),
                ObjectMappers.newServerJsonMapper(),
                ObjectMappers.newClientJsonMapper(),
                ObjectMappers.newServerSmileMapper(),
                ObjectMappers.newClientSmileMapper(),
                ObjectMappers.withDefaultModules(new ObjectMapper()),
                ObjectMappers.withDefaultModules(new YAMLMapper()));
    }
}
