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

package com.palantir.remoting2.ext.refresh;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class RefreshableTest {

    private static final Object O1 = new Object();
    private static final Object O2 = new Object();

    @Test
    public void testInitialRefreshableIsEmpty() {
        assertThat(Refreshable.empty().getAndClear()).isEmpty();
    }

    @Test
    public void testGetterClearsValue() {
        Refreshable<Object> refreshable = Refreshable.of(O1);
        assertThat(refreshable.getAndClear()).contains(O1);
        assertThat(refreshable.getAndClear()).isEmpty();
    }

    @Test
    public void testWhenRefreshableIsEmpty_setterSetsNewValue() {
        Refreshable<Object> refreshable = Refreshable.empty();
        assertThat(refreshable.set(O1)).isEmpty();
        assertThat(refreshable.getAndClear()).contains(O1);
    }

    @Test
    public void testWhenRefreshableIsNonEmpty_setterSetsNewValue() {
        Refreshable<Object> refreshable = Refreshable.of(O1);
        assertThat(refreshable.set(O2)).contains(O1);
        assertThat(refreshable.getAndClear()).contains(O2);
    }
}
