/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import feign.codec.Decoder;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;

public final class InputStreamDecoder implements HandlerDecoder {

    @Override
    public boolean canHandle(Response response, Type type) {
        return type.equals(InputStream.class);
    }

    @Override
    public Object decode(Response response, Type type, Decoder valueDecoder) throws IOException {
        byte[] body = Util.toByteArray(response.body().asInputStream());
        return new ByteArrayInputStream(body);
    }

}
