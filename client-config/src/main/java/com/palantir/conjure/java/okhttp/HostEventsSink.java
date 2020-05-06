/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.okhttp;

/**
 * A listener for responses / exceptions coming from remote hosts.
 *
 * <p>We provide a {@code HostMetricsRegistry} implementation of this that turns these events into {@code HostMetrics}
 * for each remote host.
 */
public interface HostEventsSink {
    void record(String serviceName, String hostname, int port, int statusCode, long micros);

    void recordIoException(String serviceName, String hostname, int port);

    default HostEventCallback callback(String serviceName, String hostname, int port) {
        return new HostEventCallback() {
            @Override
            public void record(int statusCode, long micros) {
                HostEventsSink.this.record(serviceName, hostname, port, statusCode, micros);
            }

            @Override
            public void recordIoException() {
                HostEventsSink.this.recordIoException(serviceName, hostname, port);
            }
        };
    }

    interface HostEventCallback {

        void record(int statusCode, long micros);

        void recordIoException();
    }
}
