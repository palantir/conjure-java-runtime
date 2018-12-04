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

package feign;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import com.google.common.collect.Maps;
import com.google.common.net.HttpHeaders;
import feign.codec.Encoder;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.junit.Before;
import org.junit.Test;

public final class ConjureTextDelegateEncoderTest {

    private RequestTemplate requestTemplate;
    private Map<String, Collection<String>> headers;
    private Encoder delegate;
    private Encoder textDelegateEncoder;

    @Before
    public void before() {
        delegate = mock(Encoder.class);
        headers = Maps.newHashMap();
        textDelegateEncoder = new ConjureTextDelegateEncoder(delegate);
        requestTemplate = new RequestTemplate();
    }

    @Test
    public void usesDelegateWhenHeaderIsAbsent() {
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verify(delegate).encode(null, null, requestTemplate);
    }

    @Test
    public void usesDelegateWhenContentTypeIsNotTextPlain() {
        headers.put(HttpHeaders.CONTENT_TYPE, Arrays.asList(MediaType.APPLICATION_JSON));
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verify(delegate).encode(null, null, requestTemplate);
    }

    @Test
    public void doesNotUseDelegateWhenContentTypeIsTextPlain() {
        headers.put(HttpHeaders.CONTENT_TYPE, Arrays.asList(MediaType.TEXT_PLAIN));
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verifyZeroInteractions(delegate);
    }

    @Test
    public void doesNotUseDelegateWhenContentTypeHeaderHasArbitraryCase() {
        headers.put("Content-TYPE", Arrays.asList(MediaType.TEXT_PLAIN));
        requestTemplate.headers(headers);
        textDelegateEncoder.encode(null, null, requestTemplate);
        verifyZeroInteractions(delegate);
    }
}
