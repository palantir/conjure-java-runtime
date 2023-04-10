/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.serialization;

import com.palantir.logsafe.Preconditions;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class StubsGenerator {
    private static final int NUM_TYPES = 250;

    private StubsGenerator() {}

    public static void main(String[] _args) throws Exception {
        Path path = Paths.get(
                "conjure-java-jackson-serialization/src/jmh/java/com/palantir/conjure/java/serialization/Stubs.java");
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("File does not exist: " + path.toAbsolutePath());
        }
        try (OutputStream out = Files.newOutputStream(path);
                Writer rawWriter = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                PrintWriter writer = new PrintWriter(rawWriter)) {
            writer.println("/*\n" + " * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.\n"
                    + " *\n"
                    + " * Licensed under the Apache License, Version 2.0 (the \"License\");\n"
                    + " * you may not use this file except in compliance with the License.\n"
                    + " * You may obtain a copy of the License at\n"
                    + " *\n"
                    + " *     http://www.apache.org/licenses/LICENSE-2.0\n"
                    + " *\n"
                    + " * Unless required by applicable law or agreed to in writing, software\n"
                    + " * distributed under the License is distributed on an \"AS IS\" BASIS,\n"
                    + " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
                    + " * See the License for the specific language governing permissions and\n"
                    + " * limitations under the License.\n"
                    + " */\n"
                    + "\n"
                    + "package com.palantir.conjure.java.serialization;\n"
                    + "\n"
                    + "import com.fasterxml.jackson.annotation.JsonProperty;\n"
                    + "import com.google.common.reflect.TypeToken;\n"
                    + "import java.util.List;\n"
                    + "\n"
                    + "@SuppressWarnings(\"CheckStyle\")\n"
                    + "public final class Stubs {\n");

            for (int i = 0; i < NUM_TYPES; i++) {
                writer.printf(
                        "    public static final TypeToken<List<Stub%d>> LIST_%d = new TypeToken<>() {};\n", i, i);
            }

            for (int i = 0; i < NUM_TYPES; i++) {
                writer.print("    public static class Stub");
                writer.print(i);
                writer.print(' ');
                if (i > 0) {
                    writer.print("extends Stub");
                    writer.print(i - 1);
                    writer.print(' ');
                }
                writer.println('{');
                writer.printf("        @JsonProperty(\"prop%d\")\n", i);
                writer.printf("        public String prop%d;\n", i);
                writer.println("    }");
            }

            writer.println('}');
        }
    }
}
