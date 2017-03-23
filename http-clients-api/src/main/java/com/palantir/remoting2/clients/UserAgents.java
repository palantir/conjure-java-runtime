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

package com.palantir.remoting2.clients;

import com.google.common.base.Preconditions;
import java.util.Optional;

public final class UserAgents {

    public static final String DEFAULT_VALUE = "unknown";

    private UserAgents() {}

    public static String getUserAgent(String userAgent, String version) {
        return String.format("%s (%s)", userAgent, version);
    }

    /**
     * Constructs a user agent from the {@code Implementation-Title} and {@code Implementation-Version} of the package
     * of the provided class. Typically, the title and version are extracted from {@code MANIFEST.MF} entries of the Jar
     * package containing the given class. The default value for both properties is {@code DEFAULT_VALUE}.
     *
     * @deprecated use {@link UserAgents#fromClassStrict(Class)}
     */
    @Deprecated
    public static String fromClass(Class<?> clazz) {
        String userAgent = implementationTitle(clazz).orElse(DEFAULT_VALUE);
        String version = implementationVersion(clazz).orElse(DEFAULT_VALUE);
        return getUserAgent(userAgent, version);
    }

    /**
     * Constructs a user agent from the {@code Implementation-Title} and {@code Implementation-Version} of the package
     * of the provided class. The title and version are extracted from {@code MANIFEST.MF} entries of the Jar.
     *
     * @throws IllegalArgumentException if either the version or the title cannot be extracted from {@code MANIFEST.MF}.
     */
    public static String fromClassStrict(Class<?> clazz) {
        Optional<String> userAgent = implementationTitle(clazz);
        Optional<String> version = implementationVersion(clazz);

        Preconditions.checkArgument(userAgent.isPresent(), "Implementation-Title missing from the manifest of %s",
                clazz);
        Preconditions.checkArgument(version.isPresent(), "Implementation-Version missing from the manifest of %s",
                clazz);

        return getUserAgent(userAgent.get(), version.get());
    }

    private static Optional<String> implementationTitle(Class<?> clazz) {
        return Optional.ofNullable(clazz.getPackage().getImplementationTitle());
    }

    private static Optional<String> implementationVersion(Class<?> clazz) {
        return Optional.ofNullable(clazz.getPackage().getImplementationVersion());
    }
}
