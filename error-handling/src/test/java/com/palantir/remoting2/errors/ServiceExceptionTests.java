/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.errors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import org.slf4j.Logger;

public final class ServiceExceptionTests {

    @Test
    public void testLogMessage() {
        String messageTemplate = "arg1={}, arg2={}";
        Param<?>[] args = {
                SafeParam.of("arg1", "foo"),
                UnsafeParam.of("arg2", "bar")};

        assertLogMessageIsCorrect(messageTemplate, args);
    }

    @Test
    public void testLogMessageWithNoParams() {
        assertLogMessageIsCorrect("error");
    }

    @Test
    public void testExceptionMessage() {
        String messageTemplate = "arg1={}, arg2={}, arg3={}";
        Param<?>[] args = {
                SafeParam.of("arg1", "foo"),
                UnsafeParam.of("arg2", 2),
                UnsafeParam.of("arg3", null)};

        String expectedMessage = "arg1=foo, arg2=2, arg3=null";

        ServiceException ex = new ServiceException(messageTemplate, args);

        assertThat(ex.getMessage()).isEqualTo(expectedMessage);
    }

    @Test
    public void testExceptionCause() {
        Throwable cause = new RuntimeException("foo");
        AbstractServiceException ex = new ServiceException(cause, "");

        assertThat(ex.getCause()).isEqualTo(cause);
    }

    @Test
    public void testStatus() {
        int status = 399;
        AbstractServiceException ex = new ServiceException(status, "");

        assertThat(ex.getStatus()).isEqualTo(status);
    }

    @Test
    public void testDefaultStatus() {
        AbstractServiceException ex = new ServiceException("");

        assertThat(ex.getStatus()).isEqualTo(500);
    }

    @Test
    public void testErrorIdsAreUnique() {
        UUID errorId1 = UUID.fromString(new ServiceException("").getErrorId());
        UUID errorId2 = UUID.fromString(new ServiceException("").getErrorId());

        assertThat(errorId1).isNotEqualTo(errorId2);
    }

    private void assertLogMessageIsCorrect(String messageTemplate, Param... args) {
        ServiceException ex = new ServiceException(messageTemplate, args);

        String expectedMessageFormat = "Error handling request {}: " + messageTemplate;

        List<Object> expectedArgs = Lists.newArrayList();
        expectedArgs.add(SafeParam.of("errorId", ex.getErrorId()));
        expectedArgs.addAll(Lists.newArrayList(args));
        expectedArgs.add(ex);

        Logger log = mock(Logger.class);
        ex.logTo(log);

        verify(log).warn(expectedMessageFormat, expectedArgs.toArray());
        verifyNoMoreInteractions(log);
    }

}
