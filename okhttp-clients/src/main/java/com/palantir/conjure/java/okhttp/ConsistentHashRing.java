/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.okhttp;

import com.google.common.collect.ImmutableList;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;

final class ConsistentHashRing {
    private final List<HttpUrl> nodes = new ArrayList<>();

    ConsistentHashRing(ImmutableList<HttpUrl> baseUrls) {
        baseUrls.forEach(this::addNode);
    }

    void addNode(HttpUrl url) {
        nodes.add(url);
    }

    void removeNode(HttpUrl url) {
        nodes.remove(url);
    }

    Optional<HttpUrl> getNode(String nodePinValue) {
        if (nodes.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(nodes.get(hash(nodePinValue)));
    }

    private int hash(String nodePinValue) {
        return Hashing.consistentHash(Hashing.crc32c().hashString(nodePinValue, StandardCharsets.UTF_8), nodes.size());
    }
}
