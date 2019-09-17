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

package com.palantir.conjure.java.config.ssl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.assertj.core.api.HamcrestCondition;
import org.junit.ClassRule;
import org.junit.Test;

public final class DropwizardConfigTests {
    @ClassRule
    public static final DropwizardAppRule<DropwizardConfigTestsConfiguration> APP = new DropwizardAppRule<>(
            DropwizardConfigTestsServer.class,
            "src/test/resources/test-server-no-ssl.yml");

    @Test
    public void testUriInConfig() {
        assertThat(APP.getConfiguration().getPath()).is(new HamcrestCondition<>(is(Paths.get("path/to/file"))));
        assertThat(APP.getConfiguration().getUri()).is(new HamcrestCondition<>(is(URI.create("hdfs://host:1234/file"))));
        assertThat(APP.getConfiguration().getBogusScheme()).is(new HamcrestCondition<>(is(URI.create("bogus://host:1234/file"))));
    }

    public static final class DropwizardConfigTestsServer extends Application<DropwizardConfigTestsConfiguration> {

        @Override
        public void run(DropwizardConfigTestsConfiguration cfg, final Environment env) throws Exception {}
    }

    public static final class DropwizardConfigTestsConfiguration extends Configuration {

        private Path path;
        private URI uri;
        private URI bogusScheme;

        public DropwizardConfigTestsConfiguration(
                @JsonProperty("path") Path path,
                @JsonProperty("uri") URI uri,
                @JsonProperty("bogusScheme") URI bogusScheme) {
            this.path = path;
            this.uri = uri;
            this.bogusScheme = bogusScheme;
        }

        public Path getPath() {
            return path;
        }

        public URI getUri() {
            return uri;
        }

        public URI getBogusScheme() {
            return bogusScheme;
        }
    }
}
