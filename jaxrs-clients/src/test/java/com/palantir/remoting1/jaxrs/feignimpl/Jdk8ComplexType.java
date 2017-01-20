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

package com.palantir.remoting1.jaxrs.feignimpl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Optional;
import java.nio.file.Path;


public final class GoogleComplexType {
    private final Optional<GoogleComplexType> nested;
    private final Optional<String> string;
    private final Path path;

    @JsonCreator
    public GoogleComplexType(
            @JsonProperty("nested") Optional<GoogleComplexType> nested,
            @JsonProperty("string") Optional<String> string,
            @JsonProperty("path") Path path) {
        this.nested = nested;
        this.string = string;
        this.path = path;
    }

    public Optional<GoogleComplexType> getNested() {
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

        GoogleComplexType that = (GoogleComplexType) other;

        if (nested != null ? !nested.equals(that.nested) : that.nested != null) {
            return false;
        }
        if (string != null ? !string.equals(that.string) : that.string != null) {
            return false;
        }
        return path != null ? path.equals(that.path) : that.path == null;

    }

    @Override
    public int hashCode() {
        int result = nested != null ? nested.hashCode() : 0;
        result = 31 * result + (string != null ? string.hashCode() : 0);
        result = 31 * result + (path != null ? path.hashCode() : 0);
        return result;
    }
}
