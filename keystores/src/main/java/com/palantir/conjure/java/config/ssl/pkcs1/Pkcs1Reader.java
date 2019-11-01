/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.config.ssl.pkcs1;

import java.io.IOException;
import java.security.spec.RSAPrivateKeySpec;

public interface Pkcs1Reader {

    /**
     * Returns the private key represented by the provided byte array. The byte array should contain the bytes for a
     * DER-encoded PKCS#1 private key.
     *
     * @param privateKeyDerBytes the bytes for a DER-encoded PKCS#1 private key
     * @return the private key specification represented by the bytes
     * @throws IOException if an I/O error occurs while decoding the provided bytes.
     */
    RSAPrivateKeySpec readPrivateKey(byte[] privateKeyDerBytes) throws IOException;
}
