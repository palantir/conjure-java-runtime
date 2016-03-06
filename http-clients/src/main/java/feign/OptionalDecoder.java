/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Optional;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;


public final class OptionalDecoder implements HandlerDecoder {

    @Override
    public boolean canHandle(Response response, Type type) {
        return Types.getRawType(type).equals(Optional.class);
    }

    @Override
    public Object decode(Response response, Type type, Decoder valueDecoder) throws IOException {
        if (response.status() == 204) {
            return Optional.absent();
        } else {
            Object decoded = checkNotNull(valueDecoder.decode(response, getInnerType(type)),
                    "Unexpected null content for response status %d", response.status());
            return Optional.of(decoded);
        }
    }

    private static Type getInnerType(Type type) {
        ParameterizedType paramType = (ParameterizedType) type;
        return paramType.getActualTypeArguments()[0];
    }
}
