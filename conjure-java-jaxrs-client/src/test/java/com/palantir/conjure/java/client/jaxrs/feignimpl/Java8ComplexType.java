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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;
import java.util.Optional;

public final class Java8ComplexType {
    private final Optional<Java8ComplexType> nested;
    private final Optional<String> string;
    private final Path path;

    @JsonCreator
    public Java8ComplexType(
            @JsonProperty("nested") Optional<Java8ComplexType> nested,
            @JsonProperty("string") Optional<String> string,
            @JsonProperty("path") Path path) {
        this.nested = nested;
        this.string = string;
        this.path = path;
    }

    public Optional<Java8ComplexType> getNested() {
        return nested;
    }

    public Optional<String> getString() {
        return string;
    }

    public Path getPath() {
        return path;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        Java8ComplexType that = (Java8ComplexType) other;

        if (nested != null ? !nested.equals(that.nested) : that.nested != null) {
            return false;
        }
        if (string != null ? !string.equals(that.string) : that.string != null) {
            return false;
        }
        return path != null ? path.getFileName().equals(that.path.getFileName()) : that.path == null;
    }

    @Override
    public int hashCode() {
        int result = nested != null ? nested.hashCode() : 0;
        result = 31 * result + (string != null ? string.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Java8ComplexType{" + "nested=" + nested + ", string=" + string + ", path=" + path + '}';
    }
}
