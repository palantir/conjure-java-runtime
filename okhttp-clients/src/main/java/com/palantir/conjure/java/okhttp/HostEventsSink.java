/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.okhttp;

/**
 * A listener for responses / exceptions coming from remote hosts when using clients created from {@link OkHttpClients}.
 *
 * <p>We provide a {@link HostMetricsRegistry} implementation of this that turns these events into {@link HostMetrics}
 * for each remote host.
 */
public interface HostEventsSink {
    void record(String serviceName, String hostname, int port, int statusCode, long micros);

    void recordIoException(String serviceName, String hostname, int port);
}
