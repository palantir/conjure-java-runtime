/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public final class ExponentialBackoffTest {
    private static final Duration ONE_SECOND = Duration.ofSeconds(1);

    @Test
    public void testNoRetry() throws Exception {
        Random random = mock(Random.class);
        ExponentialBackoff backoff = new ExponentialBackoff(0, ONE_SECOND, random);

        assertThat(backoff.nextBackoff()).isEmpty();
    }

    @Test
    public void testRetriesCorrectNumberOfTimesAndFindsRandomBackoffWithInExponentialInterval() throws Exception {
        Random random = mock(Random.class);
        ExponentialBackoff backoff = new ExponentialBackoff(3, ONE_SECOND, random);

        when(random.nextInt(anyInt())).thenReturn(1);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND);
        verify(random).nextInt(2);

        when(random.nextInt(anyInt())).thenReturn(2);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(2));
        verify(random).nextInt(4);

        when(random.nextInt(anyInt())).thenReturn(3);
        assertThat(backoff.nextBackoff()).contains(ONE_SECOND.multipliedBy(3));
        verify(random).nextInt(8);

        assertThat(backoff.nextBackoff()).isEmpty();
    }
}
