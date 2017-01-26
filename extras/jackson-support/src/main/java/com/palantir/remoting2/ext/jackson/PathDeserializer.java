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

/*
 * Derived from: https://github.com/FasterXML/jackson-databind/blob/2.7/src/main/java/com/fasterxml/jackson/databind/ext/PathDeserializer.java
 *
 * Copyright 2016 The Apache Software Foundation (Jackson / FasterXML)
 *
 * The Apache Software Foundation licenses this file to you under the Apache
 * License, version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.palantir.remoting2.ext.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class PathDeserializer extends StdScalarDeserializer<Path> {
    private static final long serialVersionUID = 1;

    public PathDeserializer() {
        super(Path.class);
    }

    @Override
    public Path deserialize(JsonParser parser, DeserializationContext ctxt) throws IOException {
        JsonToken token = parser.getCurrentToken();
        if (token != null) {
            if (token.isScalarValue()) {
                return Paths.get(parser.getValueAsString());
            }
            // 16-Oct-2015: should we perhaps allow JSON Arrays (of Strings) as well?
        }
        throw ctxt.mappingException(Path.class, token);
    }
}
