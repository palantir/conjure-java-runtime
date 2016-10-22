/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.tracing;


import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.TimeUnit;
import org.junit.Test;

public final class EventsTest {

    @Test
    public void testTypes() throws Exception {
        assertThat(Events.clientStart(1, TimeUnit.SECONDS))
                .isEqualTo(ImmutableEvent.builder().epochMicroSeconds(1_000_000).type("cs").build());
        assertThat(Events.clientReceive(1, TimeUnit.SECONDS))
                .isEqualTo(ImmutableEvent.builder().epochMicroSeconds(1_000_000).type("cr").build());
        assertThat(Events.serverReceive(1, TimeUnit.SECONDS))
                .isEqualTo(ImmutableEvent.builder().epochMicroSeconds(1_000_000).type("sr").build());
        assertThat(Events.serverSend(1, TimeUnit.SECONDS))
                .isEqualTo(ImmutableEvent.builder().epochMicroSeconds(1_000_000).type("ss").build());
    }
}
