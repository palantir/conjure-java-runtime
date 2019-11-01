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

package com.palantir.conjure.java.config.ssl;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.immutables.value.Value;

@Value.Immutable
@Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE, jdkOnly = true)
@JsonSerialize(as = ImmutablePemX509Certificate.class)
@JsonDeserialize(as = ImmutablePemX509Certificate.class)
public abstract class PemX509Certificate {

    /**
     * A X509.1 certificate or certificate chain in PEM format encoded in UTF-8.
     *
     * <p>The certificates must be delimited by the the begin and end {@code CERTIFICATE} markers.
     */
    public abstract String pemCertificate();

    public static PemX509Certificate of(String pemCertificate) {
        return ImmutablePemX509Certificate.builder().pemCertificate(pemCertificate).build();
    }
}
