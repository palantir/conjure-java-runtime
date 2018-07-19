/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.tracing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public final class TraceTest {

    @Test
    public void constructTrace_emptyTraceId() {
        assertThatThrownBy(() -> new Trace(false, ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
