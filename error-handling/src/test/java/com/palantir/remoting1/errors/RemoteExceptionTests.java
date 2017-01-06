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

package com.palantir.remoting1.errors;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import java.util.List;
import org.apache.commons.lang3.SerializationUtils;
import org.junit.Test;

public final class RemoteExceptionTests {

    @Test
    public void testJavaSerialization() {
        SerializableError error = SerializableError.of(
                "message", IllegalArgumentException.class, toList(Thread.currentThread().getStackTrace()));
        RemoteException expected = new RemoteException(error, 500);
        RemoteException actual = SerializationUtils.deserialize(SerializationUtils.serialize(expected));
        assertThat(expected).isEqualToComparingFieldByField(actual);
    }

    private static List<StackTraceElement> toList(StackTraceElement[] elements) {
        ImmutableList.Builder<StackTraceElement> list = ImmutableList.builder();
        for (StackTraceElement element : elements) {
            list.add(element);
        }
        return list.build();
    }
}
