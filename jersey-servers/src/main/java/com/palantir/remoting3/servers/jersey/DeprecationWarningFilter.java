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

package com.palantir.remoting3.servers.jersey;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.net.HttpHeaders;
import com.palantir.logsafe.SafeArg;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Inserts a deprecation warning header into the HTTP response when the Jersey resource (or one of its implemented
 * interfaces) is marked as {@link Deprecated}.
 */
@Provider
public final class DeprecationWarningFilter implements ContainerResponseFilter {
    public static final DeprecationWarningFilter INSTANCE = new DeprecationWarningFilter();

    private static final Logger log = LoggerFactory.getLogger(DeprecationWarningFilter.class);

    private static final Cache<String, Boolean> deprecationCache = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .build();

    private DeprecationWarningFilter() {}

    @Context
    private ExtendedUriInfo uriInfo;
    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
            throws IOException {
        try {
            if (resourceInfo != null
                    && resourceInfo.getResourceClass() != null
                    && resourceInfo.getResourceMethod() != null) {
                Method resourceMethod = resourceInfo.getResourceMethod();
                String key = resourceInfo.getResourceClass().getCanonicalName() + "#" + resourceMethod.getName();
                boolean isDeprecated = false;
                try {
                    isDeprecated = deprecationCache.get(key,
                            () -> hasAnnotationInHierarchy(resourceMethod, Deprecated.class));
                } catch (ExecutionException e) {
                    log.warn("Failed to determine resource method in filter invocation, "
                            + "assuming method is not deprecated");
                }

                if (isDeprecated) {
                    String path = Optional.ofNullable(uriInfo)
                            .map(ExtendedUriInfo::getMatchedModelResource)
                            .map(Resource::getPath)
                            .orElse("(unknown)");

                    log.warn("Client called deprecated API endpoint",
                            SafeArg.of("class", resourceInfo.getResourceClass().getCanonicalName()),
                            SafeArg.of("method", resourceInfo.getResourceMethod().getName()),
                            SafeArg.of("path", path),
                            SafeArg.of("userAgent", requestContext.getHeaderString(HttpHeaders.USER_AGENT)));

                    MultivaluedMap<String, Object> headers = responseContext.getHeaders();
                    // Warning header as per https://tools.ietf.org/html/rfc7234#section-5.5.7
                    headers.putSingle(HttpHeaders.WARNING, formatWarning(path));
                }
            }
        } catch (Throwable e) {
            // TODO(rfink): ResourceInfo#getResourceMethod sometimes throws. Why?
            log.warn("Failed to determine resource method in filter invocation, "
                    + "assuming method is not deprecated", e);
        }
    }

    // Used in tests in okhttp-clients test
    public static String formatWarning(String path) {
        return "299 - \"Service API endpoint is deprecated: " + path + "\"";
    }

    // Copied from Hummingbird.
    @SuppressWarnings("checkstyle:InnerAssignment")
    private static <T extends Annotation> boolean hasAnnotationInHierarchy(Method method, Class<T> annotationClazz) {
        Class<?> currentClazz = method.getDeclaringClass();

        // Scan class hierarchy first...
        do {
            Method methodOnCurrentClazz;
            try {
                methodOnCurrentClazz = currentClazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                // Keep searching class hierarchy for method
                continue;
            }

            T annotation = methodOnCurrentClazz.getAnnotation(annotationClazz);

            if (annotation != null) {
                return true;
            }
        } while ((currentClazz = currentClazz.getSuperclass()) != null);

        // Now check the interfaces...
        for (Class<?> interfaceClazz : method.getDeclaringClass().getInterfaces()) {
            Method methodOnCurrentClazz;
            try {
                methodOnCurrentClazz = interfaceClazz.getDeclaredMethod(method.getName(), method.getParameterTypes());
            } catch (NoSuchMethodException e) {
                // Doesn't exist, continue
                continue;
            }

            T annotation = methodOnCurrentClazz.getAnnotation(annotationClazz);

            if (annotation != null) {
                return true;
            }
        }

        return false;
    }
}
