/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.jackson.optimizations;

import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import java.util.List;

/**
 * Applies jackson optimization modules based on supported JVMs and best practices.
 * This allows us to quickly, transparently change between implementations such as Afterburner, Blackbird, or none at
 * all.
 */
public final class ObjectMapperOptimizations {

    private static final boolean NO_OPTIMIZATIONS =
            // These optimizations are not used within graalvm because they leverage dynamic class creation The
            // nativeimage check should not be refactored into a utility method which may reduce our ability to
            // tree-shake.
            System.getProperty("org.graalvm.nativeimage.imagecode") != null
                    // This may be globally configured with a system property
                    || readProperty(
                            "com.palantir.conjure.java.jackson.optimizations.disabled", shouldDisableByDefault());

    @SuppressWarnings("AfterburnerJavaIncompatibility")
    public static List<? extends com.fasterxml.jackson.databind.Module> createModules() {
        return NO_OPTIMIZATIONS ? List.of() : List.<com.fasterxml.jackson.databind.Module>of(new AfterburnerModule());
    }

    private ObjectMapperOptimizations() {}

    /**
     * We disable afterburner optimizations by default on java 16+ where internal access is
     * restricted by https://openjdk.java.net/jeps/396 and https://openjdk.java.net/jeps/403.
     */
    private static boolean shouldDisableByDefault() {
        return Runtime.version().feature() >= 16;
    }

    private static boolean readProperty(String property, boolean defaultValue) {
        return Boolean.parseBoolean(System.getProperty(property, Boolean.toString(defaultValue)));
    }
}
