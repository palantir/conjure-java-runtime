/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;

public interface HandlerDecoder {

    boolean canHandle(Response response, Type type);

    Object decode(Response response, Type type, Decoder valueDecoder) throws IOException;

}
