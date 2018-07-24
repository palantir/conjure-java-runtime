/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

import static com.palantir.logsafe.testing.Assertions.assertThatLoggableExceptionThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.palantir.logsafe.SafeArg;
import com.palantir.remoting3.ext.jackson.ObjectMappers;
import com.palantir.remoting3.jaxrs.TestBase;
import feign.codec.Decoder;
import feign.jackson.JacksonDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public final class RejectNullDecoderTest extends TestBase {

    private final Map<String, Collection<String>> headers = Maps.newHashMap();
    private final Decoder textDelegateDecoder = new RejectNullDecoder(
            new JacksonDecoder(ObjectMappers.newClientObjectMapper()));

    @Test
    public void throws_safe_nullpointerexception_when_body_is_null() {
        Response response = Response.create(200, "OK", headers, null, StandardCharsets.UTF_8);

        assertThatLoggableExceptionThrownBy(() -> textDelegateDecoder.decode(response, List.class))
                .hasMessage("Unexpected null body")
                .hasArgs(SafeArg.of("status", 200));
    }

    @Test
    public void works_fine_when_body_is_not_null() throws Exception {
        Response response = Response.create(200, "OK", headers, "[1, 2, 3]", StandardCharsets.UTF_8);
        Object decodedObject = textDelegateDecoder.decode(response, List.class);
        assertThat(decodedObject).isEqualTo(ImmutableList.of(1, 2, 3));
    }
}
