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
 * Derived from: https://github.com/FasterXML/jackson-datatype-jdk7/blob/master/src/main/java/com/fasterxml/jackson/datatype/jdk7/Jdk7Module.java
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

import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.nio.file.Path;

public final class ShimJdk7Module extends SimpleModule {
    private static final long serialVersionUID = 1L;

    public ShimJdk7Module() {
        super(ShimJdk7Module.class.getCanonicalName());

        final JsonSerializer<Object> stringSer = ToStringSerializer.instance;
        addSerializer(Path.class, stringSer);
        addDeserializer(Path.class, new PathDeserializer());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj;
    }
}
