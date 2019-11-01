/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.client.jaxrs.feignimpl;

import com.palantir.conjure.java.okhttp.OkhttpTraceInterceptor;
import feign.Contract;
import feign.MethodMetadata;
import java.lang.reflect.Method;

public final class PathTemplateHeaderEnrichmentContract extends AbstractDelegatingContract {
    /**
     * No longer used.
     *
     * @deprecated no longer used
     */
    @Deprecated public static final char OPEN_BRACE_REPLACEMENT = '\0';
    /**
     * No longer used.
     *
     * @deprecated no longer used
     */
    @Deprecated public static final char CLOSE_BRACE_REPLACEMENT = '\1';

    public PathTemplateHeaderEnrichmentContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> _targetType, Method _method, MethodMetadata metadata) {
        metadata.template().header(OkhttpTraceInterceptor.PATH_TEMPLATE_HEADER, metadata.template().method()
                + " "
                + metadata.template()
                        .url()
                        // escape from feign string interpolation
                        // See RequestTemplate.expand
                        .replace("{", "{{"));
    }
}
