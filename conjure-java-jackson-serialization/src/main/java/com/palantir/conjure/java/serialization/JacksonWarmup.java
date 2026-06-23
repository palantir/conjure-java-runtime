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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.logger.SafeLogger;
import com.palantir.logsafe.logger.SafeLoggerFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Synchronously serializes a representative set of payload shapes through both the server and dialogue/client
 * {@link ObjectMapper}s so the JIT settles on the bimorphic {@code BeanPropertyWriter.serializeAsField} branch
 * (typed vs untyped serializer) with full type-profile coverage before customer traffic perturbs it.
 *
 * <p>Without this warmup, a hot host that gets restarted and then receives a perturbing workload can re-enter
 * {@code unstable_if} deopt thrash on the JSON serialization path. The bytecode site is the
 * {@code if (_typeSerializer == null)} check in {@code BeanPropertyWriter.serializeAsField}
 * (<a href="https://github.com/FasterXML/jackson-databind/blob/jackson-databind-2.21.4/src/main/java/com/fasterxml/jackson/databind/ser/BeanPropertyWriter.java#L731-L735">jackson-databind
 * 2.21.4 source</a>): properties feeding monomorphic serializers take one branch while properties feeding
 * polymorphic serializers (Conjure unions, {@code @JsonTypeInfo} types) take the other. The JIT speculates
 * one branch, hits the other, deopts, recompiles, and repeats - manifesting as kernel/system CPU from
 * safepoints, code-cache invalidation, and recompilation. Throughput regressions of ~10x have been
 * observed in production when this fires.
 *
 * <p>One might expect C2 to learn the bimorphic profile after a few deopts and stop. It does not: HotSpot
 * suppresses re-profiling at sites that have already produced an {@code uncommon_trap} (the
 * {@code too_many_traps} predicate, see Shipilev's
 * <a href="https://shipilev.net/jvm/anatomy-quarks/29-uncommon-traps/">"Uncommon Traps" article</a>), so the
 * site stays compiled for the wrong branch and keeps deoptimizing. The upstream fix is
 * <a href="https://github.com/openjdk/jdk/pull/28966">openjdk/jdk#28966</a>, which switches the predicate to
 * {@code too_many_traps_or_recompiles}. Until that lands and propagates, this class works around the bug
 * by feeding C2 a bimorphic profile at startup for the Jackson serialization path.
 *
 * <p>The warmup payloads include both kinds of fields in the same object so each iteration exercises both branches,
 * letting C2 record a bimorphic profile for {@code serializeAsField} during warmup rather than learning it from
 * production traffic. The bimorphic dispatch this leaves behind is essentially free (a single null check plus
 * an inline cache with two entries), and is many orders of magnitude cheaper than the recompilation storm it
 * prevents, even on hosts that only ever exercise one branch in steady state.
 *
 * <p>See also OpenJDK issues:<ul>
 * <li> <a href="https://bugs.openjdk.org/browse/JDK-8330258">JDK-8330258</a> </li>
 * <li> <a href="https://bugs.openjdk.org/browse/JDK-8374307">JDK-8374307</a> </li>
 * </ul>
 */
public final class JacksonWarmup {

    private static final SafeLogger log = SafeLoggerFactory.get(JacksonWarmup.class);

    // Each iteration runs every mapper against every payload once, so per (mapper, payload) we hit ITERATIONS
    // top-level writeValue invocations and many more invocations of the shared inner methods like
    // BeanPropertyWriter.serializeAsField (one per field per payload). This is comfortably above HotSpot's
    // tiered-compilation invocation thresholds (Tier4InvocationThreshold defaults to 5_000) so C2 settles on
    // the bimorphic profile during warmup. Total warmup wall-clock is O(1s) on modern hardware.
    static final int ITERATIONS = 10_000;

    private JacksonWarmup() {}

    /**
     * Runs the warmup against fresh server and dialogue/client {@link ObjectMapper}s built via the same
     * conjure-java factories used at runtime. {@link com.fasterxml.jackson.databind.ser.BeanPropertyWriter} and the
     * associated serializer classes are loaded once per classloader; compiling them through these mapper instances
     * settles JIT state that is reused by the production mappers held inside conjure-undertow and dialogue.
     * @return count of payloads warmed up
     */
    public static long run() {
        return run(
                ObjectMappers.newServerObjectMapper(),
                ObjectMappers.newClientObjectMapper(),
                ObjectMappers.newServerCborMapper(),
                ObjectMappers.newClientCborMapper(),
                ObjectMappers.newServerJsonMapper(),
                ObjectMappers.newClientJsonMapper(),
                ObjectMappers.newServerSmileMapper(),
                ObjectMappers.newClientSmileMapper());
    }

    /**
     * Runs the warmup against the given mappers. Visible for callers that want to fold additional, application-specific
     * mappers into the warmup pass.
     * @param mappers {@link ObjectMapper}s to warm up
     * @return count of payloads warmed up
     */
    public static long run(ObjectMapper... mappers) {
        long startNanos = System.nanoTime();
        long count = 0;
        try (OutputStream sink = new DiscardOutputStream()) {
            List<Object> payloads = buildPayloads();
            // Iteration is the outer loop and mappers is the inner loop so each iteration exercises every mapper.
            // The hot methods being warmed (BeanPropertyWriter.serializeAsField, the per-field serializers) are
            // JVM-wide compiled, so interleaving lets every mapper contribute to the type profile rather than
            // letting the first mapper dominate and the rest only confirm it.
            List<ObjectMapper> active = new ArrayList<>(Arrays.asList(mappers));
            for (int i = 0; i < ITERATIONS && !active.isEmpty(); i++) {
                for (Iterator<ObjectMapper> it = active.iterator(); it.hasNext(); ) {
                    ObjectMapper mapper = it.next();
                    try {
                        for (Object payload : payloads) {
                            mapper.writeValue(sink, payload);
                            count++;
                        }
                    } catch (IOException | RuntimeException e) {
                        log.warn(
                                "Jackson ObjectMapper warmup failed; continuing without it",
                                SafeArg.of("mapperClass", mapper.getClass().getCanonicalName()),
                                e);
                        // Drop the mapper from the active set so we don't spam logs on every subsequent iteration.
                        it.remove();
                    }
                }
            }
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            log.info(
                    "Jackson warmup complete",
                    SafeArg.of("mappers", mappers.length),
                    SafeArg.of("count", count),
                    SafeArg.of("elapsedMillis", elapsedMillis));
        } catch (IOException | RuntimeException | Error e) {
            // Warmup is best-effort: payloads are static and validated by tests, so a failure here indicates a
            // classpath/module-version mismatch rather than a runtime input problem.
            // Log and let the warmup complete for other mappers - a degraded JIT profile is better than
            // refusing to serve traffic.
            log.warn("Jackson warmup failed; continuing without it", e);
        }
        return count;
    }

    private static List<Object> buildPayloads() {
        List<Variant> variants = List.of(
                new Variant.StringVariant("warmup-string"),
                new Variant.IntVariant(42),
                new Variant.LongVariant(123_456_789_012L),
                new Variant.DoubleVariant(3.14159d),
                new Variant.BooleanVariant(true),
                new Variant.ListVariant(List.of("a", "b", "c")),
                new Variant.MapVariant(Map.of("k1", "v1", "k2", "v2")));

        // A payload that interleaves monomorphic fields (concrete types - _typeSerializer == null) with
        // polymorphic fields (sealed interface with @JsonTypeInfo - _typeSerializer != null). Both branches of
        // BeanPropertyWriter.serializeAsField are exercised on every iteration.
        Payload mixed = new Payload(
                "warmup-name",
                42,
                123_456_789_012L,
                3.14159d,
                true,
                List.of("tag-a", "tag-b"),
                Map.of("k1", "v1", "k2", "v2"),
                Optional.of("optional-note"),
                variants.get(0),
                variants,
                Map.of("first", variants.get(1), "second", variants.get(2)),
                Optional.of(variants.get(3)));

        // A payload that drops the optionals to exercise the null/empty paths through the same writers.
        Payload sparse = new Payload(
                "sparse",
                0,
                0L,
                0d,
                false,
                List.of(),
                Map.of(),
                Optional.empty(),
                variants.get(4),
                List.of(variants.get(5)),
                Map.of(),
                Optional.empty());

        return Stream.concat(Stream.of(mixed, sparse), variants.stream()).toList();
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = Variant.StringVariant.class, name = "string"),
        @JsonSubTypes.Type(value = Variant.IntVariant.class, name = "int"),
        @JsonSubTypes.Type(value = Variant.LongVariant.class, name = "long"),
        @JsonSubTypes.Type(value = Variant.DoubleVariant.class, name = "double"),
        @JsonSubTypes.Type(value = Variant.BooleanVariant.class, name = "boolean"),
        @JsonSubTypes.Type(value = Variant.ListVariant.class, name = "list"),
        @JsonSubTypes.Type(value = Variant.MapVariant.class, name = "map")
    })
    sealed interface Variant {
        record StringVariant(String value) implements Variant {}

        record IntVariant(int value) implements Variant {}

        record LongVariant(long value) implements Variant {}

        record DoubleVariant(double value) implements Variant {}

        record BooleanVariant(boolean value) implements Variant {}

        record ListVariant(List<String> value) implements Variant {}

        record MapVariant(Map<String, String> value) implements Variant {}
    }

    record Payload(
            String name,
            int intField,
            long longField,
            double doubleField,
            boolean booleanField,
            List<String> tags,
            Map<String, String> labels,
            Optional<String> note,
            Variant primary,
            List<Variant> variants,
            Map<String, Variant> namedVariants,
            Optional<Variant> optionalVariant) {}

    /**
     * Sink that discards all bytes and ignores {@code close()}. {@link OutputStream#nullOutputStream()} cannot be reused
     * across {@link ObjectMapper#writeValue(OutputStream, Object)} calls because Jackson closes the target after each
     * write (per {@code JsonGenerator.Feature.AUTO_CLOSE_TARGET}) and the JDK sink throws {@code IOException("Stream
     * closed")} on subsequent writes.
     */
    private static final class DiscardOutputStream extends OutputStream {
        @Override
        public void write(int _byteValue) {}

        @Override
        public void write(byte[] _buf, int _off, int _len) {}

        @Override
        public void close() {}
    }
}
