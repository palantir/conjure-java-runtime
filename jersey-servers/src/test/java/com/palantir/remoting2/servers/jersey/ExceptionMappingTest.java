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

package com.palantir.remoting2.servers.jersey;


import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting.api.errors.ErrorType;
import com.palantir.remoting.api.errors.RemoteException;
import com.palantir.remoting.api.errors.SerializableError;
import com.palantir.remoting.api.errors.ServiceException;
import com.palantir.remoting2.ext.jackson.ObjectMappers;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.junit.DropwizardAppRule;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.ServerErrorException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.glassfish.jersey.client.JerseyClientBuilder;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;

public final class ExceptionMappingTest {

    @ClassRule
    public static final DropwizardAppRule<Configuration> APP =
            new DropwizardAppRule<>(ExceptionMappersTestServer.class, "src/test/resources/test-server.yml");
    private static final Response.Status SERVER_EXCEPTION_STATUS = Response.Status.SERVICE_UNAVAILABLE;
    private static final Response.Status WEB_EXCEPTION_STATUS = Response.Status.EXPECTATION_FAILED;
    private static final int REMOTE_EXCEPTION_STATUS_CODE = 400;

    private WebTarget target;

    @Before
    public void before() {
        String endpointUri = "http://localhost:" + APP.getLocalPort();
        JerseyClientBuilder builder = new JerseyClientBuilder();
        Client client = builder.build();
        target = client.target(endpointUri);
    }

    /**
     * These tests confirm that {@link WebApplicationException}s are handled by the
     * {@link WebApplicationExceptionMapper} rather than the {@link RuntimeExceptionMapper}
     */
    @Test
    public void testForbiddenException() throws NoSuchMethodException, SecurityException {
        Response response = target.path("throw-forbidden-exception").request().get();
        assertThat(response.getStatus(), is(Status.FORBIDDEN.getStatusCode()));
    }

    @Test
    public void testNotFoundException() throws NoSuchMethodException, SecurityException {
        Response response = target.path("throw-not-found-exception").request().get();
        assertThat(response.getStatus(), is(Status.NOT_FOUND.getStatusCode()));
    }

    @Test
    public void testServerErrorException() throws NoSuchMethodException, SecurityException {
        Response response = target.path("throw-server-error-exception").request().get();
        assertThat(response.getStatus(), is(SERVER_EXCEPTION_STATUS.getStatusCode()));
    }

    @Test
    public void testWebApplicationException() throws NoSuchMethodException, SecurityException {
        Response response = target.path("throw-web-application-exception").request().get();
        assertThat(response.getStatus(), is(WEB_EXCEPTION_STATUS.getStatusCode()));
    }

    @Test
    public void testRemoteException() throws NoSuchMethodException, SecurityException, IOException {
        Response response = target.path("throw-remote-exception").request().get();
        assertThat(response.getStatus(), is(REMOTE_EXCEPTION_STATUS_CODE));
        String body =
                new String(ByteStreams.toByteArray(response.readEntity(InputStream.class)), StandardCharsets.UTF_8);

        SerializableError error = ObjectMappers.newClientObjectMapper().readValue(body, SerializableError.class);
        assertThat(error.errorCode(), is("errorCode"));
        assertThat(error.errorName(), is("errorName"));

        // Check that message is passed through even if different from errorCode.
        Map<String, Object> rawError =
                ObjectMappers.newClientObjectMapper().readValue(body, new TypeReference<Map<String, Object>>() {});
        assertThat(rawError.get("message"), equalTo("message"));
    }

    @Test
    public void testServiceException() throws NoSuchMethodException, SecurityException, IOException {
        Response response = target.path("throw-service-exception").request().get();
        assertThat(response.getStatus(), is(ErrorType.INVALID_ARGUMENT.httpErrorCode()));
        String body =
                new String(ByteStreams.toByteArray(response.readEntity(InputStream.class)), StandardCharsets.UTF_8);

        SerializableError error = ObjectMappers.newClientObjectMapper().readValue(body, SerializableError.class);
        assertThat(error.errorCode(), is(ErrorType.INVALID_ARGUMENT.code().toString()));
        assertThat(error.errorName(), is(ErrorType.INVALID_ARGUMENT.name()));

        Map<String, Object> rawError =
                ObjectMappers.newClientObjectMapper().readValue(body, new TypeReference<Map<String, Object>>() {});
        assertThat(rawError.get("errorCode"), equalTo(ErrorType.INVALID_ARGUMENT.code().toString()));
        assertThat(rawError.get("exceptionClass"), equalTo(ErrorType.INVALID_ARGUMENT.code().toString()));
        assertThat(rawError.get("errorName"), equalTo(ErrorType.INVALID_ARGUMENT.name()));
        assertThat(rawError.get("message"),
                equalTo("Refer to the server logs with this errorInstanceId: " + rawError.get("errorInstanceId")));
        assertThat(rawError.get("parameters"), equalTo(ImmutableMap.of("arg", "value")));
    }

    public static class ExceptionMappersTestServer extends Application<Configuration> {
        @Override
        public final void run(Configuration config, final Environment env) throws Exception {
            env.jersey().register(HttpRemotingJerseyFeature.INSTANCE);
            env.jersey().register(new ExceptionTestResource());
        }
    }

    public static final class ExceptionTestResource implements ExceptionTestService {
        @Override
        public String throwForbiddenException() {
            throw new ForbiddenException();
        }

        @Override
        public String throwNotFoundException() {
            throw new NotFoundException();
        }

        @Override
        public String throwServerErrorException() {
            throw new ServerErrorException(SERVER_EXCEPTION_STATUS);
        }

        @Override
        public String throwWebApplicationException() {
            throw new WebApplicationException(WEB_EXCEPTION_STATUS);
        }

        @Override
        public String throwRemoteException() {
            throw new RemoteException(SerializableError.builder()
                    .errorCode("errorCode")
                    .errorName("errorName")
                    .message("message").build(),
                    REMOTE_EXCEPTION_STATUS_CODE);
        }

        @Override
        public String throwServiceException() {
            throw new ServiceException(ErrorType.INVALID_ARGUMENT, SafeArg.of("arg", "value"));
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface ExceptionTestService {
        @GET
        @Path("/throw-forbidden-exception")
        String throwForbiddenException();

        @GET
        @Path("/throw-not-found-exception")
        String throwNotFoundException();

        @GET
        @Path("/throw-server-error-exception")
        String throwServerErrorException();

        @GET
        @Path("/throw-web-application-exception")
        String throwWebApplicationException();

        @GET
        @Path("/throw-remote-exception")
        String throwRemoteException();

        @GET
        @Path("/throw-service-exception")
        String throwServiceException();
    }
}
