/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.jaxrs.feignimpl;

import com.palantir.conjure.java.tracing.okhttp3.OkhttpTraceInterceptor;
import feign.Contract;
import feign.MethodMetadata;
import java.lang.reflect.Method;

public final class PathTemplateHeaderEnrichmentContract extends AbstractDelegatingContract {
    public static final char OPEN_BRACE_REPLACEMENT = '\0';
    public static final char CLOSE_BRACE_REPLACEMENT = '\1';

    public PathTemplateHeaderEnrichmentContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata) {
        metadata.template().header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER,
                metadata.template().method() + " "
                        + metadata.template().url()
                            .replace('{', OPEN_BRACE_REPLACEMENT)
                            .replace('}', CLOSE_BRACE_REPLACEMENT));
    }
}
