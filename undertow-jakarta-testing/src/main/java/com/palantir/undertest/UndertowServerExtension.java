/*
 * (c) Copyright 2022 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.undertest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.errorprone.annotations.CheckReturnValue;
import com.google.errorprone.annotations.MustBeClosed;
import com.palantir.conjure.java.serialization.ObjectMappers;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeRuntimeException;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.URLDecodingHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.FilterInfo;
import io.undertow.servlet.api.ServletInfo;
import io.undertow.servlet.util.ImmediateInstanceFactory;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.ws.rs.ext.ContextResolver;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.glassfish.jersey.CommonProperties;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.servlet.ServletContainer;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

@SuppressWarnings("NullAway")
public final class UndertowServerExtension implements BeforeAllCallback, AfterAllCallback {

    private final String contextPath;

    private Undertow server;

    private CloseableHttpClient httpClient;

    private List<ServletInfo> servlets = new ArrayList<>();
    private List<FilterInfo> filters = new ArrayList<>();

    // LinkedHashMap preserves order
    private Map<String, String> filterUrlMapping = new LinkedHashMap<>();

    private List<Object> jerseyObjects = new ArrayList<>();

    public static UndertowServerExtension create() {
        return new UndertowServerExtension("/");
    }

    public static UndertowServerExtension create(String contextPath) {
        return new UndertowServerExtension(contextPath);
    }

    private UndertowServerExtension(String contextPath) {
        this.contextPath = contextPath;
    }

    public UndertowServerExtension servlet(ServletInfo servlet) {
        servlets.add(servlet);
        return this;
    }

    public UndertowServerExtension filter(FilterInfo filter) {
        filters.add(filter);
        return this;
    }

    public UndertowServerExtension filterUrlMapping(String name, String url) {
        Preconditions.checkArgument(
                filterUrlMapping.put(name, url) == null, "name already existed", SafeArg.of("name", name));
        return this;
    }

    public UndertowServerExtension jersey(Object jerseyObject) {
        jerseyObjects.add(jerseyObject);
        return this;
    }

    @Override
    public void beforeAll(ExtensionContext _context) throws ServletException {
        DeploymentInfo servletBuilder = Servlets.deployment()
                .setDeploymentName("test")
                .setContextPath(contextPath)
                .setClassLoader(UndertowServerExtension.class.getClassLoader());

        servletBuilder.addServlets(servlets);
        servletBuilder.addFilters(filters);

        filterUrlMapping.forEach((key, value) -> {
            servletBuilder.addFilterUrlMapping(key, value, DispatcherType.REQUEST);
        });

        if (!jerseyObjects.isEmpty()) {
            ResourceConfig jerseyConfig = new ResourceConfig()
                    .property(CommonProperties.FEATURE_AUTO_DISCOVERY_DISABLE, true)
                    .property(ServerProperties.WADL_FEATURE_DISABLE, true)
                    .register(new SimpleExceptionMapper())
                    .register(JacksonFeature.withoutExceptionMappers())
                    .register(new ObjectMapperProvider(ObjectMappers.newServerObjectMapper()));
            jerseyObjects.forEach(jerseyConfig::register);

            servletBuilder.addServlet(Servlets.servlet(
                            "jersey",
                            ServletContainer.class,
                            new ImmediateInstanceFactory<>(new ServletContainer(jerseyConfig)))
                    .addMapping("/*"));
        }

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();

        server = Undertow.builder()
                .setIoThreads(1)
                .setWorkerThreads(1)
                .addHttpListener(0, "0.0.0.0")
                .setServerOption(UndertowOptions.DECODE_URL, false)
                .setHandler(Handlers.path()
                        .addPrefixPath(
                                contextPath, new URLDecodingHandler(manager.start(), StandardCharsets.UTF_8.name())))
                .build();
        server.start();

        httpClient = HttpClients.custom().disableRedirectHandling().build();
    }

    @Provider
    public static final class ObjectMapperProvider implements ContextResolver<ObjectMapper> {

        private final ObjectMapper mapper;

        public ObjectMapperProvider(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public ObjectMapper getContext(Class<?> _type) {
            return mapper;
        }
    }

    @Override
    public void afterAll(ExtensionContext _context) throws IOException {
        if (server != null) {
            server.stop();
        }
        if (httpClient != null) {
            httpClient.close();
        }
    }

    @MustBeClosed
    @CheckReturnValue
    public CloseableHttpResponse makeRequest(ClassicHttpRequest request) throws IOException {
        return httpClient.execute(HttpHost.create(URI.create("http://localhost:" + getLocalPort())), request);
    }

    public interface ThrowableConsumer<T> {
        void accept(T arg) throws Exception;
    }

    public void runRequest(ClassicHttpRequest request, ThrowableConsumer<CloseableHttpResponse> handler) {
        try (CloseableHttpResponse response = makeRequest(request)) {
            handler.accept(response);
        } catch (IOException e) {
            throw new SafeRuntimeException("Failed to make http request", e);
        } catch (Exception e) {
            throw new SafeRuntimeException("Failed to respond to request", e);
        }
    }

    public void get(String path, ThrowableConsumer<CloseableHttpResponse> handler) {
        runRequest(new HttpGet(path), handler);
    }

    public int getLocalPort() {
        return ((InetSocketAddress) server.getListenerInfo().iterator().next().getAddress()).getPort();
    }
}
