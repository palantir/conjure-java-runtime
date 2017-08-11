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

package com.palantir.remoting3.config.ssl.pkcs1;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.security.spec.RSAPrivateKeySpec;
import java.util.ServiceLoader;
import org.junit.Assume;
import org.junit.Test;

public final class Pkcs1ReadersTests {

    @Test
    public void testReadPrivateKeyFailsIfNoProvidersPresent() throws IOException {
        Assume.assumeFalse(ServiceLoader.load(Pkcs1Reader.class).iterator().hasNext());

        try {
            Pkcs1Readers.getInstance();
            fail();
        } catch (IllegalStateException ex) {
            assertThat(ex.getMessage(), containsString("No Pkcs1Reader services were present"));
        }
    }

    @Test
    public void testReadPrivateKeySucceedsIfProviderPresent() throws IOException {
        Assume.assumeTrue(ServiceLoader.load(Pkcs1Reader.class).iterator().hasNext());

        RSAPrivateKeySpec privateKeySpec = Pkcs1Readers.getInstance().readPrivateKey(TestConstants.PRIVATE_KEY_DER);
        assertThat(TestConstants.MODULUS, is(privateKeySpec.getModulus()));
        assertThat(TestConstants.PRIVATE_EXPONENT, is(privateKeySpec.getPrivateExponent()));
    }

}
