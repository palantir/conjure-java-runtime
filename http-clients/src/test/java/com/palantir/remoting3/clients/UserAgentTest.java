/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

import org.junit.Test;

public final class UserAgentTest {

    @Test
    public void testCorrectHeaderFormatWithNodeId() {
        UserAgent baseUserAgent = UserAgent.of(UserAgent.Agent.of("service", "1.0.0"), "myNode");
        assertThat(UserAgents.format(baseUserAgent)).isEqualTo("service/1.0.0 (nodeId:myNode)");

        UserAgent derivedAgent = baseUserAgent.addAgent(UserAgent.Agent.of("remoting", "2.0.0"));
        assertThat(UserAgents.format(derivedAgent)).isEqualTo("service/1.0.0 (nodeId:nodeId), remoting/2.0.0");
    }

    @Test
    public void testCorrectHeaderFormatWithoutNodeId() {
        UserAgent baseUserAgent = UserAgent.of(UserAgent.Agent.of("service", "1.0.0"));
        assertThat(UserAgents.format(baseUserAgent)).isEqualTo("service/1.0.0");

        UserAgent derivedAgent = baseUserAgent.addAgent(UserAgent.Agent.of("remoting", "2.0.0"));
        assertThat(UserAgents.format(derivedAgent)).isEqualTo("service/1.0.0, remoting/2.0.0");
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
}
