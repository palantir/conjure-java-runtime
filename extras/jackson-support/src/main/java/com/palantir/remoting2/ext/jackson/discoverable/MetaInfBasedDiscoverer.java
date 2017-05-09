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

package com.palantir.remoting2.ext.jackson.discoverable;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads subclasses based on information found in files within the {@code META-INF/services} folder of a jar.
 */
public final class MetaInfBasedDiscoverer implements SubtypeDiscoverer {
    private static final String META_INF_SERVICES = "META-INF/services/";
    private Logger log = LoggerFactory.getLogger(MetaInfBasedDiscoverer.class);

    /**
     * {@inheritDoc}.
     */
    public List<Class<?>> discoverSubtypes(Class<?> clazz) {
        return subTypeInfoFilesFor(clazz).stream()
                .flatMap(this::loadClassesFromInfoFile)
                .collect(Collectors.toList());
    }

    private Stream<? extends Class<?>> loadClassesFromInfoFile(URL infoFileUrl) {
        try (Stream<String> lines = Files.lines(Paths.get(infoFileUrl.getPath()))) {
            return lines.flatMap(fullyQualifiedSubclassName -> {
                try {
                    return Stream.of(classLoader().loadClass(fullyQualifiedSubclassName.trim()));
                } catch (ClassNotFoundException e) {
                    log.info("Unable to load class {}", fullyQualifiedSubclassName);
                    return Stream.empty();
                }
            });
        } catch (IOException e) {
            log.warn("Error while reading subtype list from: {}", infoFileUrl, e);
            return Stream.empty();
        }
    }

    private List<URL> subTypeInfoFilesFor(Class<?> clazz) {
        try {
            return Collections.list(classLoader().getResources(META_INF_SERVICES + clazz.getName()));
        } catch (IOException e) {
            log.warn("Unable to get resource {}/{}", META_INF_SERVICES, clazz.getName(), e);
            return Collections.emptyList();
        }
    }

    private ClassLoader classLoader() {
        return getClass().getClassLoader();
    }
}
