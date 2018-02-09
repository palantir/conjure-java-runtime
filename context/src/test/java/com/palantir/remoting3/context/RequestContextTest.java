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

package com.palantir.remoting3.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.palantir.remoting3.context.RequestContext.MappedContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public final class RequestContextTest {
    public static final String TEST_CONTEXT_KEY = "contextKey";
    @Mock
    private Contextual original;
    @Mock
    private Contextual copy;

    @Before
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(original.taskCopy()).thenReturn(copy);

        RequestContext.set(TEST_CONTEXT_KEY, original);
    }

    @After
    public void after() {
        RequestContext.getAndClear();
    }

    @Test
    public void testRequestContextCopyIsIndependent() throws Exception {
        MappedContext context = RequestContext.currentContext();
        MappedContext contextCopy = context.deepCopy();

        RequestContext.setContext(contextCopy);
        verify(original, times(1)).onUnset();
        verify(copy, times(1)).onSet();

        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).contains(copy);

        RequestContext.setString("otherKey", "value");
        assertThat(context).doesNotContainKeys("otherKey");
    }

    @Test
    public void testClearAndGetRequetContextResetsContext() {
        MappedContext context = RequestContext.getAndClear();

        verify(original, times(1)).onUnset();
        assertThat(context).containsEntry(TEST_CONTEXT_KEY, original);
        assertThat(RequestContext.get(TEST_CONTEXT_KEY)).isEmpty();
    }
}
