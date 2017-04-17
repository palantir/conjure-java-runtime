/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http.config;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.palantir.remoting.http.ObjectMappers;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;

/**
 * Tests for {@link InternalServiceUrls}.
 */
public final class InternalServiceUrlsTests {

    private static final ObjectMapper MAPPER = ObjectMappers.guavaJdk7();

    @Test
    public void testSingleUrl() throws IOException {
        assertFixtureDeserializes("fixtures/singleUrl.json", InternalServiceUrls.of("https://localhost:5000"));
    }

    @Test
    public void testManyUrls() throws IOException {
        assertFixtureDeserializes("fixtures/manyUrls.json", InternalServiceUrls.of(
                ImmutableSet.of("https://localhost:5000", "https://localhost:4000")));
    }


    public static void assertFixtureDeserializes(String fixture, InternalServiceUrls urls) throws IOException {
        InputStream is = ServiceUrlsTests.class.getClassLoader().getResourceAsStream(fixture);
        InternalServiceUrls deserialized = MAPPER.readValue(is, InternalServiceUrls.class);
        assertEquals(urls, deserialized);
    }

}
