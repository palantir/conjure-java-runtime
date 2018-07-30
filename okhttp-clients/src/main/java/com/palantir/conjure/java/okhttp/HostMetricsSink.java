/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.okhttp;

/**
 * A destination for recording host metrics.
 */
public interface HostMetricsSink {
    void record(String serviceName, String hostname, int port, int statusCode, long micros);

    void recordIoException(String serviceName, String hostname, int port);
}
