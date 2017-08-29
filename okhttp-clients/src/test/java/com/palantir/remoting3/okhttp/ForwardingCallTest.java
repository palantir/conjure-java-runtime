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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import okhttp3.Call;
import org.junit.Test;

public final class ForwardingCallTest {

    @Test
    public void testClone_returnsForwardingCall() throws Exception {
        Call delegate = mock(Call.class);
        ForwardingCall call = new ForwardingCall(delegate);
        assertThat(call.clone()).isInstanceOf(ForwardingCall.class);
        verify(delegate).clone();
    }
}
