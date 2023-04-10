/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.LRUMap;
import com.fasterxml.jackson.databind.util.LookupCache;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 4, time = 4)
@Warmup(iterations = 4, time = 4)
@Fork(
        value = 1,
        jvmArgs = {"-Xmx6g", "-Xms6g"})
public class TypeFactoryBenchmarks {

    private static final int NUM_TYPES = 160; // 250;
    private static final AtomicInteger current = new AtomicInteger();
    private static final Class<?>[] TYPES = new Class<?>[NUM_TYPES];
    private static final Object[] OBJECTS = new Object[NUM_TYPES];

    private static final TypeFactory factory = TypeFactory.defaultInstance()
            .withCache((LookupCache<Object, JavaType>) new LRUMap<Object, JavaType>(16, 100));
    private static final ObjectMapper defaultMapper = new ObjectMapper();

    static {
        for (int i = 0; i < NUM_TYPES; i++) {
            try {
                TYPES[i] = Class.forName("com.palantir.conjure.java.serialization.Stubs$Stub" + i);
                OBJECTS[i] = TYPES[i].getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Threads(32)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public final JavaType typeFactory() {
        return factory.constructType(TYPES[current.incrementAndGet() % NUM_TYPES]);
    }

    @Threads(32)
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public final byte[] serialize() throws IOException {
        Object object = OBJECTS[current.incrementAndGet() % NUM_TYPES];
        return defaultMapper.writerFor(factory.constructType(object.getClass())).writeValueAsBytes(object);
    }

    public static void main(String[] _args) throws RunnerException {
        new Runner(new OptionsBuilder()
                        .include(TypeFactoryBenchmarks.class.getSimpleName() + ".typeFactory")
                        .addProfiler(JavaFlightRecorderProfiler.class)
                        .build())
                .run();
    }
}
