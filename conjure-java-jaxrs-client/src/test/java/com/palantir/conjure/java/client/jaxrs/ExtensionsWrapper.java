/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs;

import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class ExtensionsWrapper {
    private ExtensionsWrapper() {}

    public interface BeforeAndAfter<T> extends BeforeEachCallback, AfterEachCallback {
        T getResource();
    }

    public static BeforeAndAfter<MockWebServer> toExtension(MockWebServer webServer) {
        return new BeforeAndAfter<MockWebServer>() {
            @Override
            public MockWebServer getResource() {
                return webServer;
            }

            @Override
            public void afterEach(ExtensionContext _context) throws Exception {
                webServer.shutdown();
            }

            @Override
            public void beforeEach(ExtensionContext _context) throws Exception {
                webServer.start();
            }
        };
    }
}
