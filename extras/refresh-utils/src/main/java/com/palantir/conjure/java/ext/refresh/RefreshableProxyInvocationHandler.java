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

package com.palantir.conjure.java.ext.refresh;

import com.google.common.base.Preconditions;
import com.google.common.reflect.AbstractInvocationHandler;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nonnull;

/**
 * A delegating {@link InvocationHandler} that requests a new delegate whenever a given {@link Refreshable} changes.
 * Useful for constructing dynamic proxies based on live-reloadable configuration.
 */
public final class RefreshableProxyInvocationHandler<R, T> extends AbstractInvocationHandler {

    private final Refreshable<R> refreshable;
    private final Function<R, T> delegateSupplier;

    private T delegate;

    private RefreshableProxyInvocationHandler(Refreshable<R> refreshable, Function<R, T> delegateSupplier) {
        this.refreshable = refreshable;
        this.delegateSupplier = delegateSupplier;

        Optional<R> initialRefreshable = refreshable.getAndClear();
        Preconditions.checkArgument(
                initialRefreshable.isPresent(),
                "Cannot construct %s with empty initial refreshable",
                getClass().getSimpleName());
        delegate = delegateSupplier.apply(initialRefreshable.get());
    }

    public static <R, T> RefreshableProxyInvocationHandler<R, T> create(
            Refreshable<R> refreshable, Function<R, T> delegateSupplier) {
        return new RefreshableProxyInvocationHandler<>(refreshable, delegateSupplier);
    }

    @Override
    protected Object handleInvocation(@Nonnull Object _proxy, @Nonnull Method method, @Nonnull Object[] args)
            throws Throwable {
        updateDelegate();
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private void updateDelegate() {
        refreshable.getAndClear().ifPresent(r -> delegate = delegateSupplier.apply(r));
    }
}
