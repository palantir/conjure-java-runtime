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

import com.fasterxml.jackson.module.blackbird.BlackbirdModule;
import java.util.List;

/**
 * Applies jackson optimization modules based on supported JVMs and best practices.
 * This allows us to quickly, transparently change between implementations such as Afterburner, Blackbird, or none at
 * all.
 */
public final class ObjectMapperOptimizations {

    // Blackbird generates classes at runtime via LambdaMetafactory, which native images cannot do.
    private static final boolean NATIVE_IMAGE = System.getProperty("org.graalvm.nativeimage.imagecode") != null;

    /** Equivalent to {@link #createModules(boolean) createModules(false)}: no optimization modules. */
    public static List<? extends com.fasterxml.jackson.databind.Module> createModules() {
        return createModules(false);
    }

    /**
     * Optionally registers the Blackbird module, which speeds up (de)serialization by generating accessors at
     * runtime rather than using reflection. At one point this returned an {@code AfterburnerModule}, however
     * afterburner is not supported on any supported LTS Java release anymore.
     *
     * <p>Blackbird generates a class per accessor via {@code LambdaMetafactory}. This is bounded and safe when a
     * service reuses a small set of long-lived {@code ObjectMapper}s, but creating mappers per-request leaks
     * compressed class space (https://github.com/FasterXML/jackson-modules-base/issues/147), so it is opt-in.
     * Optimizations are always disabled within native images, where runtime class generation is unsupported.
     */
    public static List<? extends com.fasterxml.jackson.databind.Module> createModules(boolean useBlackbird) {
        if (!useBlackbird || NATIVE_IMAGE) {
            return List.of();
        }
        return List.of(new BlackbirdModule());
    }

    private ObjectMapperOptimizations() {}
}
