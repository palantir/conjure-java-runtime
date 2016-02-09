/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.remoting.http;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.common.collect.Multimap;
import org.immutables.value.Value;
import org.immutables.value.Value.Style.ImplementationVisibility;

@JsonDeserialize(as = ImmutableQueryMap.class)
@JsonSerialize(as = ImmutableQueryMap.class)
@Value.Immutable
@Value.Style(visibility = ImplementationVisibility.PACKAGE)
public abstract class QueryMap {
    public abstract Multimap<String, String> queryMap();

    public static QueryMap of(Multimap<String, String> queryMap) {
        return ImmutableQueryMap.builder().queryMap(queryMap).build();
    }
}
