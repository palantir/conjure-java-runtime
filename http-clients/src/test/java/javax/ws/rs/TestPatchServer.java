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

package javax.ws.rs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import com.palantir.remoting.http.ObjectMappers;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import java.io.IOException;
import java.util.Map;
import javax.ws.rs.core.MediaType;

public final class TestPatchServer extends Application<Configuration> {
    @Override
    public void run(Configuration config, final Environment env) throws Exception {
        env.jersey().register(new TestResource());
    }

    static final class TestResource implements TestService {
        private static final ObjectMapper MAPPER = ObjectMappers.vanilla();

        @Override
        public Map<String, String> getService() {
            return MAPPER.convertValue(getOriginalJsonNode(), new TypeReference<Map<String, String>>() {});
        }

        @Override
        public Map<String, String> patchService(JsonPatch patch) {
            JsonNode originalJsonNode = getOriginalJsonNode();

            JsonNode patchedNode;
            try {
                patchedNode = patch.apply(originalJsonNode);
            } catch (JsonPatchException e) {
                throw new RuntimeException(e);
            }

            return MAPPER.convertValue(patchedNode, new TypeReference<Map<String, String>>() {});
        }

        private static JsonNode getOriginalJsonNode() {
            JsonNode serviceNode;
            try {
                serviceNode = MAPPER.readTree("{\"name\":\"originalName\"}");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            return serviceNode;
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TestService {
        @GET
        @Path("/service")
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, String> getService();

        @PATCH
        @Path("/service")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, String> patchService(JsonPatch patch);
    }
}
