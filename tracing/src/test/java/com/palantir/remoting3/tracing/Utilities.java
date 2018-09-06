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

package com.palantir.remoting3.tracing;

public final class Utilities {
    private static final char[] HEX_DIGITS =
            {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    private Utilities() {}

    /**
     * Convert a long to a big-endian hex string. Hand-coded implementation is more efficient than
     * Strings.pad(Long.toHexString) because that code has to deal with mixed length longs, and then mixed length
     * amounts of padding - we want to minimise the overhead of tracing.
     */
    static String longToPaddedHex(long number) {
        char[] data = new char[16];
        data[0] = HEX_DIGITS[(int) ((number >> 60) & 0xF)];
        data[1] = HEX_DIGITS[(int) ((number >> 56) & 0xF)];
        data[2] = HEX_DIGITS[(int) ((number >> 52) & 0xF)];
        data[3] = HEX_DIGITS[(int) ((number >> 48) & 0xF)];
        data[4] = HEX_DIGITS[(int) ((number >> 44) & 0xF)];
        data[5] = HEX_DIGITS[(int) ((number >> 40) & 0xF)];
        data[6] = HEX_DIGITS[(int) ((number >> 36) & 0xF)];
        data[7] = HEX_DIGITS[(int) ((number >> 32) & 0xF)];
        data[8] = HEX_DIGITS[(int) ((number >> 28) & 0xF)];
        data[9] = HEX_DIGITS[(int) ((number >> 24) & 0xF)];
        data[10] = HEX_DIGITS[(int) ((number >> 20) & 0xF)];
        data[11] = HEX_DIGITS[(int) ((number >> 16) & 0xF)];
        data[12] = HEX_DIGITS[(int) ((number >> 12) & 0xF)];
        data[13] = HEX_DIGITS[(int) ((number >> 8) & 0xF)];
        data[14] = HEX_DIGITS[(int) ((number >> 4) & 0xF)];
        data[15] = HEX_DIGITS[(int) ((number >> 0) & 0xF)];
        return new String(data);
    }
}
