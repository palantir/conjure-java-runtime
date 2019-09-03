package com.palantir.conjure.java.okhttp;

import com.palantir.conjure.java.client.config.ImmutablesStyle;
import com.palantir.tracing.DetachedSpan;
import org.immutables.value.Value;

@Value.Modifiable
@ImmutablesStyle
interface SettableDispatcherSpan {
    DetachedSpan dispatcherSpan();
    SettableDispatcherSpan setDispatcherSpan(DetachedSpan span);

    static SettableDispatcherSpan create() {
        return ModifiableSettableDispatcherSpan.create();
    }
}
