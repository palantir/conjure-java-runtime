/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.net.HttpHeaders;
import feign.codec.Decoder;
import feign.codec.StringDecoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Collection;
import javax.ws.rs.core.MediaType;

public final class TextDecoder implements HandlerDecoder {
    private static final Decoder stringDecoder = new StringDecoder();

    @Override
    public boolean canHandle(Response response, Type type) {
        Collection<String> contentTypes = response.headers().get(HttpHeaders.CONTENT_TYPE);
        if (contentTypes == null) {
            contentTypes = ImmutableSet.of();
        }
        // In the case of multiple content types, or an unknown content type, we'll use the delegate instead.
        return contentTypes.size() == 1
                && Iterables.getOnlyElement(contentTypes, "").equals(MediaType.TEXT_PLAIN);
    }

    @Override
    public Object decode(Response response, Type type, Decoder valueDecoder) throws IOException {
        return stringDecoder.decode(response, type);
    }
}
