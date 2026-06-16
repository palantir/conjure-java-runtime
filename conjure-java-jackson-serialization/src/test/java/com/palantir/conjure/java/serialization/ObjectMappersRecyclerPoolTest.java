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

import com.fasterxml.jackson.core.util.JsonRecyclerPools;
import com.palantir.conjure.java.serialization.ObjectMappers.RecyclerPoolType;
import org.junit.jupiter.api.Test;

final class ObjectMappersRecyclerPoolTest {

    @Test
    void defaults_to_the_thread_local_recycler_pool() {
        assertThat(ObjectMappers.newServerJsonMapper().getFactory()._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.threadLocalPool());
        assertThat(ObjectMappers.newClientJsonMapper().getFactory()._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.threadLocalPool());
    }

    @Test
    void thread_local_recycler_pool_when_requested() {
        assertThat(ObjectMappers.newServerJsonMapper(RecyclerPoolType.THREAD_LOCAL)
                        .getFactory()
                        ._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.threadLocalPool());
    }

    @Test
    void shared_recycler_pool_when_requested() {
        // Covers JSON, Smile, and CBOR, for both server and client mappers.
        assertThat(ObjectMappers.newServerJsonMapper(RecyclerPoolType.SHARED)
                        .getFactory()
                        ._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.sharedConcurrentDequePool());
        assertThat(ObjectMappers.newServerSmileMapper(RecyclerPoolType.SHARED)
                        .getFactory()
                        ._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.sharedConcurrentDequePool());
        assertThat(ObjectMappers.newServerCborMapper(RecyclerPoolType.SHARED)
                        .getFactory()
                        ._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.sharedConcurrentDequePool());
        assertThat(ObjectMappers.newClientJsonMapper(RecyclerPoolType.SHARED)
                        .getFactory()
                        ._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.sharedConcurrentDequePool());
    }

    @Test
    void object_mapper_aliases_propagate_the_recycler_pool() {
        assertThat(ObjectMappers.newServerObjectMapper(RecyclerPoolType.SHARED)
                        .getFactory()
                        ._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.sharedConcurrentDequePool());
        assertThat(ObjectMappers.newServerObjectMapper().getFactory()._getRecyclerPool())
                .isSameAs(JsonRecyclerPools.threadLocalPool());
    }
}
