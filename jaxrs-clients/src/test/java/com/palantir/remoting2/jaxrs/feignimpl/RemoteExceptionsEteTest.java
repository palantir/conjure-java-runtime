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

package com.palantir.remoting2.jaxrs.feignimpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting2.clients.ByErrorCodes;
import com.palantir.remoting2.clients.ByErrorFamilies;
import com.palantir.remoting2.clients.RemoteExceptions;
import com.palantir.remoting2.jaxrs.JaxRsClient;
import com.palantir.remoting2.jaxrs.TestBase;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class RemoteExceptionsEteTest extends TestBase {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP = new DropwizardAppRule<>(ExceptionsTestServer.class,
            "src/test/resources/test-server.yml");


    private ExceptionsTestServer.TestService service;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        service = JaxRsClient.create(ExceptionsTestServer.TestService.class, "agent", createTestConfig(endpointUri));
    }

    @Test
    public void testInvalidArgument() {
        try {
            service.throwsInvalidArgument();
            fail();
        } catch (RemoteException e) {
            RemoteExceptions.handleByErrorFamily(e, ByErrorFamilies.cases()
                    .client(ex -> {
                        assertThat(ex.getError().errorCode()).isEqualTo(ErrorType.Code.INVALID_ARGUMENT.toString());
                        assertThat(ex.getError().errorName()).isEqualTo("InvalidArgument");
                        return ex;
                    })
                    .otherwise(() -> {
                        fail("Did not expect the otherwise() case");
                        return null;
                    }));

            RemoteExceptions.handleByErrorCode(e, ByErrorCodes.cases()
                    .invalidArgument(ex -> {
                        assertThat(ex.getError().errorCode()).isEqualTo(ErrorType.Code.INVALID_ARGUMENT.toString());
                        assertThat(ex.getError().errorName()).isEqualTo("InvalidArgument");
                        return ex;
                    })
                    .otherwise(() -> {
                        fail("Did not expect the otherwise() case");
                        return null;
                    }));
        }
    }

    @Test
    public void testCustomServer() {
        try {
            service.throwsCustomServer();
            fail();
        } catch (RemoteException e) {
            RemoteExceptions.handleByErrorFamily(e, ByErrorFamilies.cases()
                    .server(ex -> {
                        assertThat(ex.getError().errorCode()).isEqualTo(ErrorType.Code.CUSTOM_SERVER.toString());
                        assertThat(ex.getError().errorName()).isEqualTo("Foo");
                        return ex;
                    })
                    .otherwise(() -> {
                        fail("Did not expect the otherwise() case");
                        return null;
                    }));

            RemoteExceptions.handleByErrorCode(e, ByErrorCodes.cases()
                    .customServer(ex -> {
                        assertThat(ex.getError().errorCode()).isEqualTo(ErrorType.Code.CUSTOM_SERVER.toString());
                        assertThat(ex.getError().errorName()).isEqualTo("Foo");
                        return ex;
                    })
                    .otherwise(() -> {
                        fail("Did not expect the otherwise() case");
                        return null;
                    }));
        }
    }
}
