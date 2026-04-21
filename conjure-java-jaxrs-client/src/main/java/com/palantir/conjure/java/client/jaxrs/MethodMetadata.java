/*
 * (c) Copyright 2026 Palantir Technologies Inc. All rights reserved.
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

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"HiddenField", "MissingSummary"})
final class MethodMetadata {

    private Method method;
    private Type returnType;
    private Integer urlIndex;
    private Integer bodyIndex;
    private Integer headerMapIndex;
    private Integer queryMapIndex;
    private boolean queryMapEncoded;
    private Type bodyType;
    private RequestTemplate template = new RequestTemplate();
    private List<String> formParams = new ArrayList<String>();
    private Map<Integer, Collection<String>> indexToName = new LinkedHashMap<Integer, Collection<String>>();
    private Map<Integer, Class<? extends Expander>> indexToExpanderClass =
            new LinkedHashMap<Integer, Class<? extends Expander>>();
    private Map<Integer, Expander> indexToExpander;

    MethodMetadata() {}

    Method method() {
        return method;
    }

    MethodMetadata method(Method method) {
        this.method = method;
        return this;
    }

    Type returnType() {
        return returnType;
    }

    MethodMetadata returnType(Type returnType) {
        this.returnType = returnType;
        return this;
    }

    Integer urlIndex() {
        return urlIndex;
    }

    MethodMetadata urlIndex(Integer urlIndex) {
        this.urlIndex = urlIndex;
        return this;
    }

    Integer bodyIndex() {
        return bodyIndex;
    }

    MethodMetadata bodyIndex(Integer bodyIndex) {
        this.bodyIndex = bodyIndex;
        return this;
    }

    Integer headerMapIndex() {
        return headerMapIndex;
    }

    MethodMetadata headerMapIndex(Integer headerMapIndex) {
        this.headerMapIndex = headerMapIndex;
        return this;
    }

    Integer queryMapIndex() {
        return queryMapIndex;
    }

    MethodMetadata queryMapIndex(Integer queryMapIndex) {
        this.queryMapIndex = queryMapIndex;
        return this;
    }

    boolean queryMapEncoded() {
        return queryMapEncoded;
    }

    MethodMetadata queryMapEncoded(boolean queryMapEncoded) {
        this.queryMapEncoded = queryMapEncoded;
        return this;
    }

    /**
     * Type corresponding to {@link #bodyIndex()}.
     */
    Type bodyType() {
        return bodyType;
    }

    MethodMetadata bodyType(Type bodyType) {
        this.bodyType = bodyType;
        return this;
    }

    RequestTemplate template() {
        return template;
    }

    List<String> formParams() {
        return formParams;
    }

    Map<Integer, Collection<String>> indexToName() {
        return indexToName;
    }

    /**
     * If {@link #indexToExpander} is null, classes here will be instantiated by newInstance.
     */
    Map<Integer, Class<? extends Expander>> indexToExpanderClass() {
        return indexToExpanderClass;
    }

    /**
     * After {@link #indexToExpanderClass} is populated, this is set by contracts that support
     * runtime injection.
     */
    MethodMetadata indexToExpander(Map<Integer, Expander> indexToExpander) {
        this.indexToExpander = indexToExpander;
        return this;
    }

    /**
     * When not null, this value will be used instead of {@link #indexToExpander()}.
     */
    Map<Integer, Expander> indexToExpander() {
        return indexToExpander;
    }
}
