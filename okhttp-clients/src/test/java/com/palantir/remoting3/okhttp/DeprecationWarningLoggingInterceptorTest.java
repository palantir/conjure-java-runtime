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

import com.palantir.remoting3.servers.jersey.DeprecationWarningFilter;
import java.io.IOException;
import java.util.List;
import org.junit.Test;

public final class DeprecationWarningLoggingInterceptorTest extends TestBase {

    @Test
    public void extractsDeprecationHeaderWhenPresent() throws IOException {
        List<String> headers = list(
                "Warning: 199 - something else",
                DeprecationWarningFilter.formatWarning("foo")
        );

        assertThat(DeprecationWarningLoggingInterceptor.extractDeprecatedPath(headers)).contains("foo");
    }

    @Test
    public void doesNotExtractIrrelevantHeaders() throws IOException {
        List<String> headers = list(
                "Warning: 199 - something else",
                "Blah"
        );

        assertThat(DeprecationWarningLoggingInterceptor.extractDeprecatedPath(headers)).isEmpty();
    }
}
