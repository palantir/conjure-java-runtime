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

package com.palantir.remoting2.config.ssl.pkcs1;

import java.io.IOException;
import java.math.BigInteger;
import java.security.spec.RSAPrivateKeySpec;
// CHECKSTYLE:OFF
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;
// CHECKSTYLE:ON

public final class SunPkcs1Reader implements Pkcs1Reader {

    @Override
    public RSAPrivateKeySpec readPrivateKey(byte[] privateKeyDerBytes) throws IOException {
        DerInputStream derStream = new DerInputStream(privateKeyDerBytes);
        DerValue[] derValues = derStream.getSequence(0);

        BigInteger modulus = derValues[1].getBigInteger();
        BigInteger privateExponent = derValues[3].getBigInteger();
        return new RSAPrivateKeySpec(modulus, privateExponent);
    }

}
