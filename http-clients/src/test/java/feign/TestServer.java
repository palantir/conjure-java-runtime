/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableMap;
import com.palantir.remoting.http.server.NoContentExceptionMapper;
import com.palantir.remoting.http.server.OptionalAsNoContentMessageBodyWriter;
import com.palantir.remoting.http.server.WebApplicationExceptionMapper;
import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.setup.Environment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;
import javax.ws.rs.Consumes;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.assertj.core.util.Strings;

public class TestServer extends Application<Configuration> {
    @Override
    public final void run(Configuration config, final Environment env) throws Exception {
        env.jersey().register(new TestResource());
        env.jersey().register(new OptionalAsNoContentMessageBodyWriter());

        // Not registering all mappers so that we can test behaviour for exceptions without registered mapper.
        env.jersey().register(new NoContentExceptionMapper());
        env.jersey().register(new WebApplicationExceptionMapper(true));
    }

    static class TestResource implements TestService {
        @Override
        public Optional<ImmutableMap<String, String>> getOptional(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return Optional.absent();
            } else {
                return Optional.of(ImmutableMap.of(value, value));
            }
        }

        @Override
        public ImmutableMap<String, String> getNonOptional(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return ImmutableMap.of();
            } else {
                return ImmutableMap.of(value, value);
            }
        }

        @Override
        public ImmutableMap<String, String> getThrowsNotFound(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                throw new NotFoundException("Not found");
            } else {
                return ImmutableMap.of(value, value);
            }
        }

        @Override
        public ImmutableMap<String, String> getThrowsNotAuthorized(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                throw new NotAuthorizedException("Not authorized");
            } else {
                return ImmutableMap.of(value, value);
            }
        }

        @Override
        public Optional<ImmutableMap<String, String>> getOptionalThrowsNotAuthorized(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                throw new NotAuthorizedException("Not authorized");
            } else {
                return Optional.of(ImmutableMap.of(value, value));
            }
        }

        @Override
        public ImmutableMap<String, String> getThrowsForbidden(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                throw new ForbiddenException("Forbidden");
            } else {
                return ImmutableMap.of(value, value);
            }
        }

        @Override
        public Optional<ImmutableMap<String, String>> getOptionalThrowsForbidden(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                throw new ForbiddenException("Forbidden");
            } else {
                return Optional.of(ImmutableMap.of(value, value));
            }
        }

        @Override
        public String getString(@Nullable String value) {
            return value;
        }

        @Override
        public InputStream writeInputStream(String bytes) {
            return new ByteArrayInputStream(bytes.getBytes(Charsets.UTF_8));
        }

        @Override
        public String readInputStream(InputStream data) {
            try {
                return new String(Util.toByteArray(data), Charsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<String> getOptionalString(@Nullable String value) {
            return Optional.fromNullable(value);
        }

        @Override
        public String getStringJson() {
            return "foo";
        }

        @Override
        public Optional<String> getOptionalStringJson() {
            return Optional.of("foo");
        }
    }

    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public interface TestService {
        @GET
        @Path("/optional")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Optional<ImmutableMap<String, String>> getOptional(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/nonOptional")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        ImmutableMap<String, String> getNonOptional(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/throwsNotFound")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        ImmutableMap<String, String> getThrowsNotFound(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/throwsNotAuthorized")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        ImmutableMap<String, String> getThrowsNotAuthorized(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/optionalThrowsNotAuthorized")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Optional<ImmutableMap<String, String>> getOptionalThrowsNotAuthorized(
                @QueryParam("value") @Nullable String value);

        @GET
        @Path("/throwsForbidden")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        ImmutableMap<String, String> getThrowsForbidden(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/optionalThrowsForbidden")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Optional<ImmutableMap<String, String>> getOptionalThrowsForbidden(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/string")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String getString(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/writeInputStream")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        InputStream writeInputStream(@QueryParam("value") @Nullable String bytes);

        @POST
        @Path("/readInputStream")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        String readInputStream(InputStream data);

        @GET
        @Path("/optionalString")
        @Consumes(MediaType.TEXT_PLAIN)
        @Produces(MediaType.TEXT_PLAIN)
        Optional<String> getOptionalString(@QueryParam("value") @Nullable String value);

        @GET
        @Path("/stringJson")
        @Produces(MediaType.APPLICATION_JSON)
        String getStringJson();

        @GET
        @Path("/optionalStringJson")
        @Produces(MediaType.APPLICATION_JSON)
        Optional<String> getOptionalStringJson();
    }
}
