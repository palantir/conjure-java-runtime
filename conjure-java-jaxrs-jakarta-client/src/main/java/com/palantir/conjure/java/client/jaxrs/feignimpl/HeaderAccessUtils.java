/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;

/**
 * Used to access headers in a case-insensitive manner. This is necessary for compatibility with OkHttp 3.3.0+ as it
 * lower-cases header names whereas we use constants from {@link com.google.common.net.HttpHeaders} where header names
 * are in Train-Case. This can be removed once {@link feign.Request} and {@link feign.Response} expose the headers as a
 * map which is case-insensitive with respect to the key. com.netflix.feign:feign-core:8.18.0 will have it for the
 * {@link feign.Response} headers due to https://github.com/Netflix/feign/pull/418.
 */
public final class HeaderAccessUtils {
    private HeaderAccessUtils() {}

    public static boolean caseInsensitiveContains(Map<String, Collection<String>> headers, String headerName) {
        for (String key : headers.keySet()) {
            if (headerName.equalsIgnoreCase(key)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Compares the keys of the map to the headerName in a case-insensitive manner and returns null if it was never
     * found.
     */
    public static Collection<String> caseInsensitiveGet(Map<String, Collection<String>> headers, String headerName) {
        Collection<String> result = new LinkedList<>();
        boolean neverFound = true;
        for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (headerName.equalsIgnoreCase(key)) {
                neverFound = false;
                result.addAll(entry.getValue());
            }
        }
        return neverFound ? null : result;
    }
}
