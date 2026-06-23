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
import com.palantir.conjure.java.serialization.JacksonWarmup.Payload;
import com.palantir.conjure.java.serialization.JacksonWarmup.Variant;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Reproducer for the JIT deoptimization storm at {@code BeanPropertyWriter.serializeAsField} that
 * {@link JacksonWarmup} works around. Forks a pair of child JVMs with {@code -XX:+PrintCompilation}, drives a
 * train-then-perturb workload through the package-protected {@link Variant} and {@link Payload} types in
 * each, and compares the resulting {@code BeanPropertyWriter.serializeAsField} C2 events.
 *
 * <p>The train phase serializes {@link Variant.StringVariant} repeatedly - every property writer it touches
 * has {@code _typeSerializer == null}, so C2 sees a monomorphic profile and may compile {@code
 * serializeAsField} with the {@code _typeSerializer != null} branch elided as cold (an {@code uncommon_trap}).
 * The perturb phase then serializes {@link Payload}, which interleaves typed ({@link Variant} fields, which
 * have {@code _typeSerializer != null}) and untyped (String, int, ...) property writers. Hitting the
 * previously-cold branch fires the trap, which PrintCompilation reports as
 * {@code made not entrant: uncommon trap} on the C2 compilation of serializeAsField. On JDKs that haven't
 * picked up <a href="https://github.com/openjdk/jdk/pull/28966">openjdk/jdk#28966</a>, C2 then keeps
 * re-deopting on every recompile - this is the storm. With {@link JacksonWarmup} the bimorphic profile is
 * established before the perturb, so the trap does not fire.
 *
 * <p>Opt-in: skipped by default because forking child JVMs is heavyweight relative to the rest of the suite
 * and the bug surface depends on the running JDK. Enable with
 * {@code ./gradlew :conjure-java-jackson-serialization:test --tests JacksonWarmupReproducerTest
 * -Djackson.warmup.reproducer=true}.
 *
 * <p>See OpenJDK issues
 * <a href="https://bugs.openjdk.org/browse/JDK-8330258">JDK-8330258</a> /
 * <a href="https://bugs.openjdk.org/browse/JDK-8374307">JDK-8374307</a>.
 */
@EnabledIfSystemProperty(named = "jackson.warmup.reproducer", matches = "true")
class JacksonWarmupReproducerTest {

    private static final SafeLogger log = SafeLoggerFactory.get(JacksonWarmupReproducerTest.class);

    private static final Path JAVA = Paths.get(System.getProperty("java.home"), "bin", "java");

    private static final String SERIALIZE_AS_FIELD =
            "com.fasterxml.jackson.databind.ser.BeanPropertyWriter::serializeAsField";

    @Test
    void warmupEliminatesSerializeAsFieldUncommonTrapDeopts() throws IOException, InterruptedException {
        List<String> withoutWarmup = forkReproducer(false);
        List<String> withWarmup = forkReproducer(true);

        Counts noWarmupCounts = parse(withoutWarmup);
        Counts warmupCounts = parse(withWarmup);

        log.info(
                "BeanPropertyWriter.serializeAsField JIT activity (no warmup)",
                SafeArg.of("c2Compiled", noWarmupCounts.c2Compiled),
                SafeArg.of("uncommonTrapDeopts", noWarmupCounts.uncommonTrapDeopts),
                SafeArg.of("otherMadeNotEntrant", noWarmupCounts.otherMadeNotEntrant),
                SafeArg.of("printCompilationLines", noWarmupCounts.relevantLines));
        log.info(
                "BeanPropertyWriter.serializeAsField JIT activity (with warmup)",
                SafeArg.of("c2Compiled", warmupCounts.c2Compiled),
                SafeArg.of("uncommonTrapDeopts", warmupCounts.uncommonTrapDeopts),
                SafeArg.of("otherMadeNotEntrant", warmupCounts.otherMadeNotEntrant),
                SafeArg.of("printCompilationLines", warmupCounts.relevantLines));

        // Sanity: C2 (tier 4) should compile serializeAsField in both runs - this verifies the train phase
        // drove enough invocations to trigger tiered compilation in the first place.
        assertThat(noWarmupCounts.c2Compiled)
                .as(
                        "Expected C2 to compile %s without warmup. Full output:\n%s",
                        SERIALIZE_AS_FIELD, String.join("\n", withoutWarmup))
                .isGreaterThan(0);
        assertThat(warmupCounts.c2Compiled)
                .as(
                        "Expected C2 to compile %s with warmup. Full output:\n%s",
                        SERIALIZE_AS_FIELD, String.join("\n", withWarmup))
                .isGreaterThan(0);

        // The bug signal: without warmup, C2's monomorphic profile from the train phase doesn't survive the
        // perturb phase - the previously-cold _typeSerializer != null branch fires an uncommon trap and
        // PrintCompilation reports "made not entrant: uncommon trap" on the serializeAsField C2 compilation.
        // If this assertion ever fails (i.e. uncommonTrapDeopts becomes 0 without warmup), the running JDK
        // likely contains the fix in openjdk/jdk#28966 and JacksonWarmup may no longer be necessary.
        assertThat(noWarmupCounts.uncommonTrapDeopts)
                .as(
                        "Expected at least one 'uncommon trap' deopt on %s without warmup. Without-warmup"
                                + " output:\n%s",
                        SERIALIZE_AS_FIELD, String.join("\n", withoutWarmup))
                .isGreaterThan(0);

        // The fix signal: warmup pre-establishes the bimorphic profile so the trap doesn't fire. If this
        // ever regresses, warmup payloads have likely drifted away from covering both branches.
        assertThat(warmupCounts.uncommonTrapDeopts)
                .as(
                        "Expected zero 'uncommon trap' deopts on %s with warmup but saw %d. With-warmup"
                                + " output:\n%s",
                        SERIALIZE_AS_FIELD, warmupCounts.uncommonTrapDeopts, String.join("\n", withWarmup))
                .isZero();
    }

    private static Counts parse(List<String> printCompilation) {
        List<String> relevant = printCompilation.stream()
                .filter(line -> line.contains(SERIALIZE_AS_FIELD))
                .toList();
        long c2Compiled = relevant.stream()
                .filter(JacksonWarmupReproducerTest::isTier4Compilation)
                .filter(line -> !line.contains("made "))
                .count();
        long uncommonTrap = relevant.stream()
                .filter(line -> line.contains("made not entrant"))
                .filter(line -> line.contains("uncommon trap"))
                .count();
        long otherMadeNotEntrant = relevant.stream()
                .filter(line -> line.contains("made not entrant"))
                .filter(line -> !line.contains("uncommon trap"))
                .count();
        return new Counts(c2Compiled, uncommonTrap, otherMadeNotEntrant, relevant);
    }

    private static boolean isTier4Compilation(String line) {
        // PrintCompilation columns: "<timestamp> <compile_id> <flags> <tier> <method> (<bytes>) [<event>]".
        // Tier 4 is C2. We look for a standalone "4" token before the method name.
        int methodIdx = line.indexOf(SERIALIZE_AS_FIELD);
        return methodIdx > 0 && line.substring(0, methodIdx).matches(".*\\b4\\b\\s+$");
    }

    private static List<String> forkReproducer(boolean withWarmup) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                        JAVA.toString(),
                        "-XX:+PrintCompilation",
                        "-cp",
                        System.getProperty("java.class.path"),
                        Reproducer.class.getName(),
                        Boolean.toString(withWarmup))
                .redirectErrorStream(true);

        Process process = builder.start();
        List<String> lines;
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            lines = reader.lines().collect(Collectors.toUnmodifiableList());
        }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "Reproducer JVM timed out after 60s. Captured output:\n" + String.join("\n", lines));
        }
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new IllegalStateException(
                    "Reproducer JVM exited " + exitCode + ". Captured output:\n" + String.join("\n", lines));
        }
        return lines;
    }

    private record Counts(
            long c2Compiled, long uncommonTrapDeopts, long otherMadeNotEntrant, List<String> relevantLines) {}

    /**
     * Forked child main. Trains the JIT into a monomorphic profile by serializing a record whose property
     * writers all have {@code _typeSerializer == null}, then perturbs by serializing a {@link Payload} that
     * interleaves typed and untyped property writers. Designed to be invoked as a separate JVM with
     * {@code -XX:+PrintCompilation}.
     */
    public static final class Reproducer {

        // Far above tiered-compilation thresholds so C2 has settled before we perturb.
        private static final int TRAIN_ITERATIONS = 200_000;
        // Enough to give C2 multiple recompile opportunities in the perturb phase.
        private static final int PERTURB_ITERATIONS = 50_000;

        public static void main(String[] args) throws IOException, InterruptedException {
            boolean withWarmup = args.length > 0 && Boolean.parseBoolean(args[0]);

            ObjectMapper mapper = ObjectMappers.newServerObjectMapper();
            if (withWarmup) {
                JacksonWarmup.run(mapper);
            }

            OutputStream sink = new DiscardOutputStream();

            // Train: only monomorphic (_typeSerializer == null) property writers.
            Variant monomorphic = new Variant.StringVariant("train");
            for (int i = 0; i < TRAIN_ITERATIONS; i++) {
                mapper.writeValue(sink, monomorphic);
            }
            // Let the C2 compiler thread drain before we change the workload shape.
            Thread.sleep(500);

            // Perturb: bimorphic - Payload has both typed (Variant fields) and untyped (String, int, ...)
            // property writers, hitting the previously-cold _typeSerializer != null branch.
            Payload mixed = buildMixedPayload();
            for (int i = 0; i < PERTURB_ITERATIONS; i++) {
                mapper.writeValue(sink, mixed);
            }
        }

        private static Payload buildMixedPayload() {
            return new Payload(
                    "warmup-name",
                    42,
                    123_456_789_012L,
                    3.14159d,
                    true,
                    List.of("tag-a", "tag-b"),
                    Map.of("k1", "v1", "k2", "v2"),
                    Optional.of("optional-note"),
                    new Variant.StringVariant("primary"),
                    List.of(new Variant.IntVariant(1), new Variant.LongVariant(2L)),
                    Map.of("first", new Variant.BooleanVariant(true)),
                    Optional.of(new Variant.DoubleVariant(2.71d)));
        }

        private static final class DiscardOutputStream extends OutputStream {
            @Override
            public void write(int _byteValue) {}

            @Override
            public void write(byte[] _buf, int _off, int _len) {}

            @Override
            public void close() {}
        }
    }
}
