/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting2.tracing;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public class TraceTest {

    @Test
    public void constructTrace_emptyTraceId() {
        assertThatThrownBy(() -> new Trace(false, ""))
            .isInstanceOf(IllegalArgumentException.class);
    }

}
