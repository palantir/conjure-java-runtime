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

package com.palantir.remoting2.errors;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public final class SerializableStackTraceElementTest {

    @Test
    public void testToStringIsHumanReadable() {
        SerializableStackTraceElement element = SerializableStackTraceElement.builder()
                .className("com.palantir.remoting2.Test")
                .methodName("getText")
                .fileName("Test.java")
                .lineNumber(2)
                .build();
        assertThat(element.toString(),
                is("com.palantir.remoting2.Test.getText(Test.java:2)"));
    }

}
