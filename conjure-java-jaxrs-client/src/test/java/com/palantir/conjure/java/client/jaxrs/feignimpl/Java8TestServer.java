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

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.palantir.conjure.java.server.jersey.ConjureJerseyFeature;
import com.palantir.undertest.UndertowServerExtension;
import feign.Util;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;

public final class Java8TestServer {
    private Java8TestServer() {}

    public static UndertowServerExtension createUndertow() {
        return UndertowServerExtension.create()
                .jersey(ConjureJerseyFeature.INSTANCE)
                .jersey(new TestResource());
    }

    static class TestResource implements TestService {
        @Override
        public Optional<ImmutableMap<String, String>> getOptional(@Nullable String value) {
            if (Strings.isNullOrEmpty(value)) {
                return Optional.empty();
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
            return new ByteArrayInputStream(bytes.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public String readInputStream(InputStream data) {
            try {
                return new String(Util.toByteArray(data), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public Optional<String> getOptionalString(@Nullable String value) {
            return Optional.ofNullable(value);
        }

        @Override
        public Java8ComplexType getJava8ComplexType(Java8ComplexType value) {
            return value;
        }

        @Override
        public List<String> getNullList() {
            return null;
        }

        @Override
        public Set<String> getNullSet() {
            return null;
        }

        @Override
        public Map<String, String> getNullMap() {
            return null;
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

        @POST
        @Path("/complexType")
        @Consumes(MediaType.APPLICATION_JSON)
        @Produces(MediaType.APPLICATION_JSON)
        Java8ComplexType getJava8ComplexType(Java8ComplexType value);

        @POST
        @Path("/list")
        @Produces(MediaType.APPLICATION_JSON)
        List<String> getNullList();

        @POST
        @Path("/set")
        @Produces(MediaType.APPLICATION_JSON)
        Set<String> getNullSet();

        @POST
        @Path("/map")
        @Produces(MediaType.APPLICATION_JSON)
        Map<String, String> getNullMap();
    }
}
