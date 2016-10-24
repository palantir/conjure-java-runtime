/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting1.tracing;

public enum SpanType {
    /**
     * Indicates that this span encapsulates server-side work of an RPC call. This is typically the outermost span
     * of a set of calls made within one service as a result of an incoming RPC call.
     */
    SERVER_INCOMING,

    /**
     * Indicates that this is the innermost span encapsulating remote work, typically the last span opened by an RPC
     * client.
     */
    CLIENT_OUTGOING
}
