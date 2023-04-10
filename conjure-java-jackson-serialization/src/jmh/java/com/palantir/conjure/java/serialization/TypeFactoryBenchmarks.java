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
import com.fasterxml.jackson.datatype.guava.GuavaTypeModifier;
import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.profile.LinuxPerfAsmProfiler;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@Measurement(iterations = 8, time = 10)
@Warmup(iterations = 4, time = 8)
@Fork(
        value = 1,
        jvmArgs = {"-Xmx6g", "-Xms6g"})
public class TypeFactoryBenchmarks {

    private static final int NUM_TYPES = 250;
    private static final AtomicInteger current = new AtomicInteger();
    private static final Type[] LIST_TYPES = new Type[NUM_TYPES];
    private static final Class<?>[] TYPES = new Class<?>[NUM_TYPES];
    private static final Object[] OBJECTS = new Object[NUM_TYPES];

    private static final LRUMap<Object, JavaType> CACHE = new LRUMap<Object, JavaType>(16, 200);
    private static final TypeFactory factory = TypeFactory.defaultInstance()
            .withModifier(new GuavaTypeModifier())
            .withCache((LookupCache<Object, JavaType>) CACHE);

    @SuppressWarnings("unused")
    private static final TypeFactory factory2 = TypeFactory.defaultInstance()
            .withCache(new LookupCache<Object, JavaType>() {
                @Override
                public int size() {
                    return 0;
                }

                @Override
                @Nullable
                public JavaType get(Object _key) {
                    return null;
                }

                @Override
                public JavaType put(Object _key, JavaType _value) {
                    return null;
                }

                @Override
                public JavaType putIfAbsent(Object _key, JavaType _value) {
                    return null;
                }

                @Override
                public void clear() {}
            });

    private static final ObjectMapper defaultMapper = new ObjectMapper();

    static {
        for (int i = 0; i < NUM_TYPES; i++) {
            try {
                TYPES[i] = Class.forName("com.palantir.conjure.java.serialization.Stubs$Stub" + i);
                LIST_TYPES[i] = ((TypeToken<?>)
                                Stubs.class.getDeclaredField("LIST_" + i).get(null))
                        .getType();
                OBJECTS[i] = TYPES[i].getConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Threads(14)
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public final JavaType typeFactory() {
        return factory.constructType(TYPES[current.incrementAndGet() % NUM_TYPES]);
    }

    @Threads(14)
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public final JavaType typeFactoryConstructSpecializedType() {
        if (false && ThreadLocalRandom.current().nextInt(10_000) == 0) {
            System.out.println("===== CACHE BEGIN =====");
            CACHE.contents((key, javaType) ->
                    System.out.printf("%8s %8s # %10s => %s\n", key.hashCode(), key.getClass(), key, javaType));
            System.out.println("===== CACHE END =====");
            System.exit(0);
        }
        Type type = LIST_TYPES[current.incrementAndGet() % NUM_TYPES];
        JavaType javaType = factory.constructType(type);
        return factory.constructSpecializedType(javaType, ImmutableList.class);
    }

    @Threads(14)
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public final byte[] serialize() throws IOException {
        Object object = OBJECTS[current.incrementAndGet() % NUM_TYPES];
        return defaultMapper.writerFor(factory.constructType(object.getClass())).writeValueAsBytes(object);
    }

    public static void main(String[] _args) throws RunnerException {
        new Runner(new OptionsBuilder()
                        .include(TypeFactoryBenchmarks.class.getSimpleName() + ".typeFactoryConstructSpecializedType")
                        .forks(1)
                        //                        .addProfiler(LinuxPerfAsmProfiler.class)
                        //                        .addProfiler(JavaFlightRecorderProfiler.class)
                        .build())
                .run();
    }

    public static final class MyList<T> extends AbstractList<T> {

        @Override
        @Nullable
        public T get(int _index) {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }
    }
}
