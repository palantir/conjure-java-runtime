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

package com.palantir.remoting3.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.Test;

public final class UserAgentTest {

    @Test
    public void validAndInvalidNodeSyntax() throws Exception {
        // Valid nodeId
        for (String nodeId : new String[] {
                "nodeId",
                "NODEID",
                "node-id",
                "node.id",
                "nodeId.",
                "192.168.0.1",
                "my.server.foo.local"
        }) {
            UserAgent.of(UserAgent.Agent.of("valid-service", "1.0.0"), nodeId);
        }

        // Invalid nodeId
        for (String nodeId : new String[] {
                ".nodeId",
                "node$",
                "node_id"
        }) {
            assertThatThrownBy(() -> UserAgent.of(UserAgent.Agent.of("valid-service", "1.0.0"), nodeId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    public void testCorrectHeaderFormatWithNodeId() {
        UserAgent baseUserAgent = UserAgent.of(UserAgent.Agent.of("service", "1.0.0"), "myNode");
        assertThat(UserAgents.format(baseUserAgent)).isEqualTo("service/1.0.0 (nodeId:myNode)");

        UserAgent derivedAgent = baseUserAgent.addAgent(UserAgent.Agent.of("remoting", "2.0.0"));
        assertThat(UserAgents.format(derivedAgent)).isEqualTo("service/1.0.0 (nodeId:myNode) remoting/2.0.0");
    }

    @Test
    public void testCorrectHeaderFormatWithoutNodeId() {
        UserAgent baseUserAgent = UserAgent.of(UserAgent.Agent.of("service", "1.0.0"));
        assertThat(UserAgents.format(baseUserAgent)).isEqualTo("service/1.0.0");

        UserAgent derivedAgent = baseUserAgent.addAgent(UserAgent.Agent.of("remoting", "2.0.0"));
        assertThat(UserAgents.format(derivedAgent)).isEqualTo("service/1.0.0 remoting/2.0.0");
    }

    @Test
    public void testInvalidServiceName() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UserAgent.Agent.of("invalid service name", "1.0.0"))
                .withMessage("Illegal agent name format: invalid service name");
    }

    @Test
    public void testInvalidNodeId() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> UserAgent.of(UserAgent.Agent.of("serviceName", "1.0.0"), "invalid node id"))
                .withMessage("Illegal node id format: invalid node id");
    }

    @Test
    public void testInvalidVersion() {
        assertThat(UserAgents.format(UserAgent.of(UserAgent.Agent.of("serviceName", "1 0 0"), "myNode")))
                .isEqualTo("serviceName/0.0.0 (nodeId:myNode)");
    }

    @Test
    public void parse_handlesPrimaryAgent() throws Exception {
        // Valid strings
        for (String agent : new String[] {
                "service/1.2",
                "service/1.2.3-2-g4658d8a",
                "service/1.2.3-rc1-2-g4658d8a",
                "service/10.20.30",
                "service/10.20.30 (nodeId:myNode)",
                }) {
            assertThat(UserAgents.format(UserAgents.parse(agent))).isEqualTo(agent).withFailMessage(agent);
        }

        // Formatting ignores non-nodeId comments
        assertThat(UserAgents.format(UserAgents.parse("service/1.2.3 (foo:bar)"))).isEqualTo("service/1.2.3");

        // Finds primary agent even when there is a prefix
        assertThat(UserAgents.format(UserAgents.parse("  service/1.2.3"))).isEqualTo("service/1.2.3");
        assertThat(UserAgents.format(UserAgents.parse("bogus  service/1.2.3"))).isEqualTo("service/1.2.3");

        // Fixes primary agent version to 0.0.0 if it cannot be parsed
        assertThat(UserAgents.format(UserAgents.parse("service/foo-1.2.3"))).isEqualTo("service/0.0.0");

        // Invalid syntax throws exception
        for (String agent : new String[] {
                "s",
                "foo|1.2.3",
                }) {
            assertThatThrownBy(() -> UserAgents.format(UserAgents.parse(agent)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Failed to parse user agent string: " + agent);
        }
    }

    @Test
    public void parse_handlesInformationalAgents() throws Exception {
        // Valid strings
        for (String agent : new String[] {
                "serviceA/1.2.3 serviceB/4.5.6",
                "serviceB/1.2.3 (nodeId:myNode) serviceB/4.5.6"
        }) {
            assertThat(UserAgents.format(UserAgents.parse(agent))).isEqualTo(agent).withFailMessage(agent);
        }

        // nodeId on informational agents is omitted
        assertThat(UserAgents.format(UserAgents.parse("serviceA/1.2.3 serviceB/4.5.6 (nodeId:myNode)")))
                .isEqualTo("serviceA/1.2.3 serviceB/4.5.6");

        // Malformed informational agents are omitted
        assertThat(UserAgents.format(UserAgents.parse("serviceA/1.2.3 serviceB|4.5.6"))).isEqualTo("serviceA/1.2.3");
    }

    @Test
    public void parse_canParseBrowserAgent() throws Exception {
        String chrome = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/61.0.3163.100 Safari/537.36";
        String expected = "Mozilla/5.0 AppleWebKit/537.36 Chrome/61.0.3163.100 Safari/537.36";
        assertThat(UserAgents.format(UserAgents.tryParse(chrome))).isEqualTo(expected);
        assertThat(UserAgents.format(UserAgents.parse(chrome))).isEqualTo(expected);
    }

    @Test
    public void tryParse_parsesWithBestEffort() throws Exception {
        // Fixes up the primary agent
        assertThat(UserAgents.format(UserAgents.tryParse(null))).isEqualTo("unknown/0.0.0");
        assertThat(UserAgents.format(UserAgents.tryParse(""))).isEqualTo("unknown/0.0.0");
        assertThat(UserAgents.format(UserAgents.tryParse("serviceA|1.2.3"))).isEqualTo("unknown/0.0.0");
        assertThat(UserAgents.format(UserAgents.tryParse("foo serviceA/1.2.3"))).isEqualTo("serviceA/1.2.3");

        // Omits malformed informational agents
        assertThat(UserAgents.format(UserAgents.tryParse("serviceA/1.2.3 bogus|1.2.3 foo bar (boom)")))
                .isEqualTo("serviceA/1.2.3");
    }
}
