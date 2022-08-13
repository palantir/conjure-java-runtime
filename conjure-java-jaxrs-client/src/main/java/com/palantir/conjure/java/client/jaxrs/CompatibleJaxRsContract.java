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
import com.palantir.conjure.java.client.jaxrs.JaxRsJakartaCompatibility.Annotations;
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

    @Override
    protected void processAnnotationOnClass(MethodMetadata data, Class<?> clz) {
        Annotation path = Annotations.PATH.getAnnotation(clz);
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
        Annotation consumes = Annotations.CONSUMES.getAnnotation(clz);
        if (consumes != null) {
            handleConsumesAnnotation(data, consumes, clz.getName());
        }
        Annotation produces = Annotations.PRODUCES.getAnnotation(clz);
        if (produces != null) {
            handleProducesAnnotation(data, produces, clz.getName());
        }
    }

    @Override
    protected void processAnnotationOnMethod(MethodMetadata data, Annotation methodAnnotation, Method method) {
        Class<? extends Annotation> annotationType = methodAnnotation.annotationType();
        Annotation http = Annotations.HTTP_METHOD.getAnnotation(annotationType);
        if (http != null) {
            String httpValue = getAnnotationValue(http);
            Preconditions.checkState(
                    data.template().method() == null,
                    "Method contains multiple HTTP methods",
                    SafeArg.of("method", method.getName()),
                    SafeArg.of("existingMethod", data.template().method()),
                    SafeArg.of("newMethod", httpValue));
            data.template().method(Preconditions.checkNotNull(httpValue, "Unexpected null HttpMethod value"));
        } else if (Annotations.PATH.matches(annotationType)) {
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
        } else if (Annotations.PRODUCES.matches(annotationType)) {
            handleProducesAnnotation(data, methodAnnotation, "method " + method.getName());
        } else if (Annotations.CONSUMES.matches(annotationType)) {
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
            if (Annotations.PATH_PARAM.matches(annotationType)) {
                String name = getAnnotationValue(parameterAnnotation);
                Preconditions.checkState(
                        Strings.emptyToNull(name) != null,
                        "PathParam.value() was empty on parameter",
                        SafeArg.of("paramIndex", paramIndex));
                nameParam(data, name, paramIndex);
                isHttpParam = true;
            } else if (Annotations.QUERY_PARAM.matches(annotationType)) {
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
            } else if (Annotations.HEADER_PARAM.matches(annotationType)) {
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
            } else if (Annotations.FORM_PARAM.matches(annotationType)) {
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
}
