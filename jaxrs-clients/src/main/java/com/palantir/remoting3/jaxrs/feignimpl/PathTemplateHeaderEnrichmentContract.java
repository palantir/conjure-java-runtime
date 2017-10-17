/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting3.jaxrs.feignimpl;

import feign.Contract;
import feign.MethodMetadata;
import java.lang.reflect.Method;

public final class PathTemplateHeaderEnrichmentContract extends AbstractDelegatingContract {

    public PathTemplateHeaderEnrichmentContract(Contract delegate) {
        super(delegate);
    }

    @Override
    protected void processMetadata(Class<?> targetType, Method method, MethodMetadata metadata) {
        metadata.template().header("hr-path-template", metadata.template().url());
    }

}
