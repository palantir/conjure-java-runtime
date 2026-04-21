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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

class FeignInvocationHandler implements InvocationHandler {

    private final Target<?> target;
    private final Map<Method, MethodHandler> methodHandlers;

    FeignInvocationHandler(Target<?> target, Map<Method, MethodHandler> methodHandlers) {
        this.target = Util.checkNotNull(target, "target");
        this.methodHandlers = Util.checkNotNull(methodHandlers, "methodHandlers for %s", target);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }

        return switch (method.getName()) {
            case "equals" -> {
                try {
                    Object otherHandler =
                            args.length > 0 && args[0] != null ? Proxy.getInvocationHandler(args[0]) : null;
                    yield equals(otherHandler);
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
            case "hashCode" -> {
                yield hashCode();
            }
            case "toString" -> {
                yield toString();
            }
            default -> {
                yield methodHandlers.get(method).invoke(args);
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FeignInvocationHandler other) {
            return target.equals(other.target);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return target.hashCode();
    }

    @Override
    public String toString() {
        return target.toString();
    }
}
