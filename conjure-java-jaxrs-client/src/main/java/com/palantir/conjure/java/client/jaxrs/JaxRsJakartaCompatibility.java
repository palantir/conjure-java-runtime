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

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.Set;
import javax.annotation.Nullable;

public final class JaxRsJakartaCompatibility {
    private JaxRsJakartaCompatibility() {}

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

    public enum Annotations {
        CONSUMES(JAVAX_CONSUMES, JAKARTA_CONSUMES),
        PRODUCES(JAVAX_PRODUCES, JAKARTA_PRODUCES),
        PATH_PARAM(JAVAX_PATH_PARAM, JAKARTA_PATH_PARAM),
        QUERY_PARAM(JAVAX_QUERY_PARAM, JAKARTA_QUERY_PARAM),
        PATH(JAVAX_PATH, JAKARTA_PATH),
        HEADER_PARAM(JAVAX_HEADER_PARAM, JAKARTA_HEADER_PARAM),
        FORM_PARAM(JAVAX_FORM_PARAM, JAKARTA_FORM_PARAM),
        HTTP_METHOD(JAVAX_HTTP_METHOD, JAKARTA_HTTP_METHOD),
        ;

        @Nullable
        private final Class<? extends Annotation> javax;

        @Nullable
        private final Class<? extends Annotation> jakarta;

        Annotations(@Nullable Class<? extends Annotation> javax, @Nullable Class<? extends Annotation> jakarta) {
            this.javax = javax;
            this.jakarta = jakarta;
        }

        public boolean matches(Class<? extends Annotation> annotation) {
            return annotation == jakarta || annotation == javax;
        }

        public boolean matches(Set<Class<?>> annotations) {
            return annotations.contains(jakarta) || annotations.contains(javax);
        }

        @Nullable
        public Annotation getAnnotation(AnnotatedElement element) {
            if (jakarta != null) {
                Annotation annotation = element.getAnnotation(jakarta);
                if (annotation != null) {
                    return annotation;
                }
            }
            if (javax != null) {
                Annotation annotation = element.getAnnotation(javax);
                return annotation;
            }

            return null;
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
