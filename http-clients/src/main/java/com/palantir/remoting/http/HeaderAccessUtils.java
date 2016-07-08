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

package com.palantir.remoting.http;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Used to access headers in a case-insensitive manner. This is necessary for compatibility with OkHttp 3.3.0+ as
 * it lower-cases header names whereas we use constants from {@link com.google.common.net.HttpHeaders} where header
 * names are in Train-Case. This can be removed once {@link feign.Request} and {@link feign.Response} expose the
 * headers as a map which is case-insensitive with respect to the key. com.netflix.feign:feign-core:8.18.0 will
 * have it for the {@link feign.Response} headers due to https://github.com/Netflix/feign/pull/418.
 */
public final class HeaderAccessUtils {
    private HeaderAccessUtils() {}

    public static boolean caseInsensitiveContains(Map<String, Collection<String>> headers, String header) {
        for (String headerName : headers.keySet()) {
            if (headerName.equalsIgnoreCase(header)) {
                return true;
            }
        }
        return false;
    }

    public static Collection<String> caseInsensitiveGet(Map<String, Collection<String>> headers, String header) {
        return caseInsensitiveMultimapOf(headers).get(header);
    }

    // returns a case-insensitive TreeMap in which the values for all keys equal modulo case are merged.
    private static Map<String, List<String>> caseInsensitiveMultimapOf(Map<String, Collection<String>> headers) {
        Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            if (!result.containsKey(headerName)) {
                result.put(headerName, new LinkedList<String>());
            }
            result.get(headerName).addAll(entry.getValue());
        }
        return result;
    }
}
