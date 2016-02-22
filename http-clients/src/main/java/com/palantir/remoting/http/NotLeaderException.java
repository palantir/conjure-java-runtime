/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import feign.RetryableException;

/**
 * An exception thrown when a server that is not the elected leader receives a request.
 *
 * This will trigger a fast failover to the next node if used with a {@link FailoverFeignTarget}.
 */
public class NotLeaderException extends RetryableException {
    private static final long serialVersionUID = 1L;

    public NotLeaderException(String message) {
        super(message, null);
    }
}
