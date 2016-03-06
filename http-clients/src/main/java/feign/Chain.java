/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import feign.codec.DecodeException;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import jersey.repackaged.com.google.common.collect.ImmutableList;

public final class Chain {

    public static Decoder firstHandler(Decoder baseDecoder, HandlerDecoder... handlers) {
        return new ChainDecoder(baseDecoder, handlers);
    }

    private static final class ChainDecoder implements Decoder {
        private final Decoder valueDecoder;
        private final List<HandlerDecoder> handlers;

        ChainDecoder(Decoder valueDecoder, HandlerDecoder... handlers) {
            this.valueDecoder = valueDecoder;
            this.handlers = ImmutableList.copyOf(handlers);
        }

        @Override
        public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {
            for (HandlerDecoder currHandler : handlers) {
                if (currHandler.canHandle(response, type)) {
                    return currHandler.decode(response, type, valueDecoder);
                }
            }

            return valueDecoder.decode(response, type);
        }
    }

    private Chain() {}

}
