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

public final class HeaderAccessUtils {
    private HeaderAccessUtils() {}

    public static boolean caseInsensitiveContains(Map<String, Collection<String>> headers, String header) {
        return caseInsensitiveGet(headers, header) != null;
    }

    public static Collection<String> caseInsensitiveGet(Map<String, Collection<String>> headers, String header) {
        return caseInsensitiveMultimapOf(headers).get(header);
    }

    private static Map<String, List<String>> caseInsensitiveMultimapOf(Map<String, Collection<String>> headers) {
        Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, Collection<String>> entry : headers.entrySet()) {
            String headerName = entry.getKey();
            List<String> values = result.get(headerName);
            if (values == null) {
                values = new LinkedList<>();
                result.put(headerName, values);
            }
            values.addAll(entry.getValue());
        }
        return result;
    }
}
