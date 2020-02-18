/*
 * (c) Copyright 2020 Palantir Technologies Inc. All rights reserved.
 */

package com.palantir.conjure.java.config.ssl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

public final class DefaultCasTest {
    @Test
    public void checkCanParseCertificates() {
        // this varies when the .crt file is updated, so not checking exactly
        assertThat(DefaultCas.getTrustManager().getAcceptedIssuers()).hasSizeGreaterThan(50);
    }
}
