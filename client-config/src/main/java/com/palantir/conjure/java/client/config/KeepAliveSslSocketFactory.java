/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.client.config;

import com.palantir.logsafe.Preconditions;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;
import javax.net.ssl.SSLSocketFactory;

/**
 * Enables {@link java.net.StandardSocketOptions#SO_KEEPALIVE}. By default this will only send a packet after two hours
 * (which is long after our timeouts), but this can be lowered to be useful in the kernel, e.g.:
 *
 * <p>net.ipv4.tcp_keepalive_time = 20 net.ipv4.tcp_keepalive_intvl = 5 net.ipv4.tcp_keepalive_probes = 3
 */
final class KeepAliveSslSocketFactory extends ForwardingSslSocketFactory {
    private final SSLSocketFactory delegate;

    KeepAliveSslSocketFactory(SSLSocketFactory delegate) {
        this.delegate = Preconditions.checkNotNull(delegate, "delegate must not be null");
    }

    @Override
    SSLSocketFactory getDelegate() {
        return delegate;
    }

    @Override
    Socket wrap(Socket socket) throws SocketException {
        socket.setKeepAlive(true);
        return socket;
    }

    @Override
    public String toString() {
        return "KeepAliveSslSocketFactory{delegate=" + delegate + '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }
        KeepAliveSslSocketFactory that = (KeepAliveSslSocketFactory) other;
        return delegate.equals(that.delegate);
    }
}
