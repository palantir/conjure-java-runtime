/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package feign;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.palantir.remoting.http.QueryMap;
import feign.codec.EncodeException;
import feign.codec.Encoder;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map.Entry;

/**
 * Encoder for {@link QueryMap} that takes the contents of the
 * map and encodes them as query parameters in the request.
 */
public final class QueryMapEncoder implements Encoder {
    private final Encoder delegate;

    public QueryMapEncoder(Encoder delegate) {
        this.delegate = delegate;
    }

    @Override
    public void encode(Object object, Type bodyType, RequestTemplate template) throws EncodeException {
        if (bodyType == QueryMap.class) {
            QueryMap queryParams = (QueryMap) object;

            Multimap<String, String> existing = ArrayListMultimap.create();
            for (Entry<String, Collection<String>> currEntry : template.queries().entrySet()) {
                existing.putAll(currEntry.getKey(), currEntry.getValue());
            }
            existing.putAll(queryParams.queryMap());

            template.queries(existing.asMap());
        } else {
            delegate.encode(object, bodyType, template);
        }
    }
}
