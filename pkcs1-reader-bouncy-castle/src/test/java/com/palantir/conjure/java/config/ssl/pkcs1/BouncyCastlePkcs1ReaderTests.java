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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.security.spec.RSAPrivateKeySpec;
import org.junit.Test;

public final class BouncyCastlePkcs1ReaderTests {

    @Test
    public void testBouncyCastlePkcs1ReaderInstalled() {
        assertThat(Pkcs1Readers.getInstance()).isInstanceOf(BouncyCastlePkcs1Reader.class);
    }

    @Test
    public void testReadPrivateKey() throws IOException {
        RSAPrivateKeySpec privateKeySpec = new BouncyCastlePkcs1Reader().readPrivateKey(TestConstants.PRIVATE_KEY_DER);
        assertThat(TestConstants.MODULUS).isEqualTo(privateKeySpec.getModulus());
        assertThat(TestConstants.PRIVATE_EXPONENT).isEqualTo(privateKeySpec.getPrivateExponent());
    }
}
