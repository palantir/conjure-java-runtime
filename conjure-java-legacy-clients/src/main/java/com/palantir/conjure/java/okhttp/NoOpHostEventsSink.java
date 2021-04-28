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

/** A no-op implementation of {@link HostEventsSink} - i.e. it discards all events. */
public enum NoOpHostEventsSink implements HostEventsSink {
    INSTANCE;

    @Override
    public void record(String _serviceName, String _hostname, int _port, int _statusCode, long _micros) {
        // do nothing
    }

    @Override
    public void recordIoException(String _serviceName, String _hostname, int _port) {
        // do nothing
    }
}
