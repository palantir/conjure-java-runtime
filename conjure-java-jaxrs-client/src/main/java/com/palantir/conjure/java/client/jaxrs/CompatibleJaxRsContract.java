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
package com.palantir.conjure.java.client.jaxrs;

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.palantir.logsafe.Preconditions;
import com.palantir.logsafe.SafeArg;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import feign.Contract;
import feign.MethodMetadata;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * This contract is based on the open-source feign implementation from feign v8.18.0
 * <a href="https://github.com/OpenFeign/feign/blob/v8.18.0/jaxrs/src/main/java/feign/jaxrs/JAXRSContract.java">
 * JAXRSContract.java</a> which is licensed under Apache 2.
 * We have modified the implementation to handle both jaxrs and jakarta annotations, easing migrations.
 */
public final class CompatibleJaxRsContract extends Contract.BaseContract {

    @Nullable
    private static final Class<? extends Annotation> JAVAX_CONSUMES = resolve("javax.ws.rs.Consumes");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_CONSUMES = resolve("jakarta.ws.rs.Consumes");

    @Nullable
    private static final Class<? extends Annotation> JAVAX_FORM_PARAM = resolve("javax.ws.rs.FormParam");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_FORM_PARAM = resolve("jakarta.ws.rs.FormParam");

    @Nullable
    private static final Class<? extends Annotation> JAVAX_HEADER_PARAM = resolve("javax.ws.rs.HeaderParam");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_HEADER_PARAM = resolve("jakarta.ws.rs.HeaderParam");

    @Nullable
    private static final Class<? extends Annotation> JAVAX_HTTP_METHOD = resolve("javax.ws.rs.HttpMethod");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_HTTP_METHOD = resolve("jakarta.ws.rs.HttpMethod");

    @Nullable
    private static final Class<? extends Annotation> JAVAX_PATH = resolve("javax.ws.rs.Path");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_PATH = resolve("jakarta.ws.rs.Path");

    @Nullable
    private static final Class<? extends Annotation> JAVAX_PATH_PARAM = resolve("javax.ws.rs.PathParam");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_PATH_PARAM = resolve("jakarta.ws.rs.PathParam");

    @Nullable
    private static final Class<? extends Annotation> JAVAX_PRODUCES = resolve("javax.ws.rs.Produces");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_PRODUCES = resolve("jakarta.ws.rs.Produces");

    @Nullable
    private static final Class<? extends Annotation> JAVAX_QUERY_PARAM = resolve("javax.ws.rs.QueryParam");

    @Nullable
    private static final Class<? extends Annotation> JAKARTA_QUERY_PARAM = resolve("jakarta.ws.rs.QueryParam");

    @Override
    protected void processAnnotationOnClass(MethodMetadata data, Class<?> clz) {
        Annotation path = getAnnotation(clz, JAKARTA_PATH, JAVAX_PATH);
        if (path != null) {
            String pathValue = Strings.emptyToNull(getAnnotationValue(path));
            Preconditions.checkState(
                    pathValue != null, "Path.value() was empty on type", SafeArg.of("type", clz.getName()));
            if (!pathValue.startsWith("/")) {
                pathValue = "/" + pathValue;
            }
            if (pathValue.endsWith("/")) {
                // Strip off any trailing slashes, since the template has already had slashes appropriately added
                pathValue = pathValue.substring(0, pathValue.length() - 1);
            }
            data.template().insert(0, pathValue);
        }
        Annotation consumes = getAnnotation(clz, JAKARTA_CONSUMES, JAVAX_CONSUMES);
        if (consumes != null) {
            handleConsumesAnnotation(data, consumes, clz.getName());
        }
        Annotation produces = getAnnotation(clz, JAKARTA_PRODUCES, JAVAX_PRODUCES);
        if (produces != null) {
            handleProducesAnnotation(data, produces, clz.getName());
        }
    }

    @Override
    protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation, Method method) {
        Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
        Annotation http = getAnnotation(annotationType, JAKARTA_HTTP_METHOD, JAVAX_HTTP_METHOD);
        if (http != null) {
            String httpValue = getAnnotationValue(http);
            Preconditions.checkState(
                    data.template().method() == null,
                    "Method contains multiple HTTP methods",
                    SafeArg.of("method", method.getName()),
                    SafeArg.of("existingMethod", data.template().method()),
                    SafeArg.of("newMethod", httpValue));
            data.template().method(Preconditions.checkNotNull(httpValue, "Unexpected null HttpMethod value"));
        } else if (annotationType == JAKARTA_PATH || annotationType == JAVAX_PATH) {
            String pathValue = Strings.emptyToNull(getAnnotationValue(methodAnnotation));
            Preconditions.checkState(
                    pathValue != null, "Path.value() was empty on method", SafeArg.of("method", method.getName()));
            if (!pathValue.startsWith("/") && !data.template().url().endsWith("/")) {
                pathValue = "/" + pathValue;
            }
            // jax-rs allows whitespace around the param name, as well as an optional regex. The contract should
            // strip these out appropriately.
            pathValue = pathValue.replaceAll("\\{\\s*(.+?)\\s*(:.+?)?\\}", "\\{$1\\}");
            data.template().append(pathValue);
        } else if (annotationType == JAKARTA_PRODUCES || annotationType == JAVAX_PRODUCES) {
            handleProducesAnnotation(data, methodAnnotation, "method " + method.getName());
        } else if (annotationType == JAKARTA_CONSUMES || annotationType == JAVAX_CONSUMES) {
            handleConsumesAnnotation(data, methodAnnotation, "method " + method.getName());
        }
    }

    private void handleProducesAnnotation(MethodMetadata data, Annotation produces, String name) {
        String[] serverProduces = getAnnotationValues(produces);
        String clientAccepts =
                serverProduces == null || serverProduces.length == 0 ? null : Strings.emptyToNull(serverProduces[0]);
        Preconditions.checkState(clientAccepts != null, "Produces.value() was empty", SafeArg.of("target", name));
        data.template().header(HttpHeaders.ACCEPT, (String) null); // remove any previous produces
        data.template().header(HttpHeaders.ACCEPT, clientAccepts);
    }

    private void handleConsumesAnnotation(MethodMetadata data, Annotation consumes, String name) {
        String[] serverConsumes = getAnnotationValues(consumes);
        String clientProduces =
                serverConsumes == null || serverConsumes.length == 0 ? null : Strings.emptyToNull(serverConsumes[0]);
        Preconditions.checkState(clientProduces != null, "Consumes.value() was empty", SafeArg.of("target", name));
        data.template().header(HttpHeaders.CONTENT_TYPE, (String) null); // remove any previous consumes
        data.template().header(HttpHeaders.CONTENT_TYPE, clientProduces);
    }

    @Override
    protected boolean processAnnotationsOnParameter(MethodMetadata data, Annotation[] annotations, int paramIndex) {
        boolean isHttpParam = false;
        for (Annotation parameterAnnotation : annotations) {
            Class<? extends Annotation> annotationType =
                    Preconditions.checkNotNull(parameterAnnotation.annotationType(), "Unexpected null annotation type");
            if (annotationType == JAKARTA_PATH_PARAM || annotationType == JAVAX_PATH_PARAM) {
                String name = getAnnotationValue(parameterAnnotation);
                Preconditions.checkState(
                        Strings.emptyToNull(name) != null,
                        "PathParam.value() was empty on parameter",
                        SafeArg.of("paramIndex", paramIndex));
                nameParam(data, name, paramIndex);
                isHttpParam = true;
            } else if (annotationType == JAKARTA_QUERY_PARAM || annotationType == JAVAX_QUERY_PARAM) {
                String name = getAnnotationValue(parameterAnnotation);
                Preconditions.checkState(
                        Strings.emptyToNull(name) != null,
                        "QueryParam.value() was empty on parameter",
                        SafeArg.of("paramIndex", paramIndex));
                Collection<String> query =
                        addTemplatedParam(data.template().queries().get(name), name);
                data.template().query(name, query);
                nameParam(data, name, paramIndex);
                isHttpParam = true;
            } else if (annotationType == JAKARTA_HEADER_PARAM || annotationType == JAVAX_HEADER_PARAM) {
                String name = getAnnotationValue(parameterAnnotation);
                Preconditions.checkState(
                        Strings.emptyToNull(name) != null,
                        "HeaderParam.value() was empty on parameter",
                        SafeArg.of("paramIndex", paramIndex));
                Collection<String> header =
                        addTemplatedParam(data.template().headers().get(name), name);
                data.template().header(name, header);
                nameParam(data, name, paramIndex);
                isHttpParam = true;
            } else if (annotationType == JAKARTA_FORM_PARAM || annotationType == JAVAX_FORM_PARAM) {
                String name = getAnnotationValue(parameterAnnotation);
                Preconditions.checkState(
                        Strings.emptyToNull(name) != null,
                        "FormParam.value() was empty on parameter",
                        SafeArg.of("paramIndex", paramIndex));
                data.formParams().add(name);
                nameParam(data, name, paramIndex);
                isHttpParam = true;
            }
        }
        return isHttpParam;
    }

    @Nullable
    private static Annotation getAnnotation(
            AnnotatedElement element, @Nullable Class<? extends Annotation> annotationType) {
        return annotationType != null ? element.getAnnotation(annotationType) : null;
    }

    @Nullable
    private static Annotation getAnnotation(
            AnnotatedElement element,
            @Nullable Class<? extends Annotation> first,
            @Nullable Class<? extends Annotation> second) {
        Annotation result = getAnnotation(element, first);
        return result != null ? result : getAnnotation(element, second);
    }

    @Nullable
    private static String getAnnotationValue(Annotation annotation) {
        return (String) getAnnotationValueInternal(annotation);
    }

    @Nullable
    private static String[] getAnnotationValues(Annotation annotation) {
        return (String[]) getAnnotationValueInternal(annotation);
    }

    @Nullable
    private static Object getAnnotationValueInternal(Annotation annotation) {
        try {
            return annotation.annotationType().getMethod("value").invoke(annotation);
        } catch (ReflectiveOperationException e) {
            throw new SafeIllegalStateException(
                    "Failed to read annotation value", e, SafeArg.of("annotationType", annotation.annotationType()));
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Class<? extends Annotation> resolve(String fqcn) {
        try {
            return (Class<? extends Annotation>) Class.forName(fqcn);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
