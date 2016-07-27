/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting.clients;

import com.google.common.base.Optional;

public final class UserAgents {

    public static final String DEFAULT_VALUE = "unknown";

    private UserAgents() {}

    public static String getUserAgent(String userAgent, String version) {
        return String.format("%s (%s)", userAgent, version);
    }

    /**
     * Constructs a user agent from the {@link java.util.jar.Attributes.Name#IMPLEMENTATION_TITLE} and {@link
     * java.util.jar.Attributes.Name#IMPLEMENTATION_VERSION} of the package of the provided class. Typically, the title
     * and version are extracted from {@code MANIFEST.MF} entries of the Jar package containing the given class. The
     * default value for both properties is "{@value DEFAULT_VALUE}".
     */
    public static String fromClass(Class<?> clazz) {
        Package classPackage = clazz.getPackage();
        String userAgent = Optional.fromNullable(classPackage.getImplementationTitle()).or(DEFAULT_VALUE);
        String version = Optional.fromNullable(classPackage.getImplementationVersion()).or(DEFAULT_VALUE);
        return getUserAgent(userAgent, version);
    }

}
