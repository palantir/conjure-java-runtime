/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.config.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import org.immutables.value.Value.Style;

/**
 * Sets the style to be used by the Immutables library.
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Style(
    visibility = Style.ImplementationVisibility.PACKAGE)
@interface PackageVisibilityImmutableStyle {}
