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

package com.palantir.conjure.java.server.jersey;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import jakarta.ws.rs.BadRequestException;
import javax.annotation.Nullable;

/**
 * Wrapper around some of Guava's {@link Preconditions} to make its exceptions more http-friendly exceptions. It
 * includes other useful web-oriented checks like {@link #checkNotEmpty(String, Object)} and excludes less relevant ones
 * (like {@link Preconditions#checkState(boolean)}.
 *
 * @deprecated Use {@link com.palantir.conjure.java.api.errors.ServiceException}.
 */
@Deprecated
public final class WebPreconditions {

    private WebPreconditions() {}

    /**
     * See {@link Preconditions#checkArgument(boolean, Object)}.
     *
     * @throws BadRequestException when <code>expression</code> is false
     */
    public static void checkArgument(boolean expression, @Nullable Object message) {
        if (!expression) {
            throw new BadRequestException(String.valueOf(message));
        }
    }

    /**
     * See {@link Preconditions#checkArgument(boolean, String, Object...)}.
     *
     * @throws BadRequestException when <code>expression</code> is false
     */
    public static void checkArgument(
            boolean expression, @Nullable String messageTemplate, @Nullable Object... messageArgs) {
        if (!expression) {
            throw new BadRequestException(format(messageTemplate, messageArgs));
        }
    }

    /**
     * See {@link Preconditions#checkArgument(boolean, Object)} and {@link Strings#isNullOrEmpty(String)}.
     *
     * @throws BadRequestException when <code>reference</code> is null or empty
     */
    public static String checkNotEmpty(String string, @Nullable Object message) {
        checkArgument(!Strings.isNullOrEmpty(string), message);

        return string;
    }

    /**
     * See {@link Preconditions#checkArgument(boolean, String, Object...)} and {@link Strings#isNullOrEmpty(String)}.
     *
     * @throws BadRequestException when <code>reference</code> is null or empty
     */
    public static String checkNotEmpty(
            String string, @Nullable String messageTemplate, @Nullable Object... messageArgs) {
        checkArgument(!Strings.isNullOrEmpty(string), messageTemplate, messageArgs);

        return string;
    }

    /**
     * See {@link Preconditions#checkNotNull(Object, Object)}.
     *
     * @throws BadRequestException when <code>reference</code> is null
     */
    public static <T> T checkNotNull(T reference, @Nullable Object message) {
        checkArgument(reference != null, message);

        return reference;
    }

    /**
     * See {@link Preconditions#checkNotNull(Object, Object)}.
     *
     * @throws BadRequestException when <code>reference</code> is null
     */
    public static <T> T checkNotNull(T reference, @Nullable String messageTemplate, @Nullable Object... messageArgs) {
        checkArgument(reference != null, messageTemplate, messageArgs);

        return reference;
    }

    /*
     * Taken almost verbatim from Guava's Preconditions#format(String, Object...) modulo some variable name
     * tweaks to make checkstyle happy.
     */
    private static String format(String template, @Nullable Object... args) {
        String nonNullTemplate = String.valueOf(template); // null -> "null"

        // start substituting the arguments into the '%s' placeholders
        StringBuilder builder = new StringBuilder(nonNullTemplate.length() + 16 * args.length);
        int templateStart = 0;
        int index = 0;
        while (index < args.length) {
            int placeholderStart = nonNullTemplate.indexOf("%s", templateStart);
            if (placeholderStart == -1) {
                break;
            }
            builder.append(nonNullTemplate.substring(templateStart, placeholderStart));
            builder.append(args[index++]);
            templateStart = placeholderStart + 2;
        }
        builder.append(nonNullTemplate.substring(templateStart));

        // if we run out of placeholders, append the extra args in square braces
        if (index < args.length) {
            builder.append(" [");
            builder.append(args[index++]);
            while (index < args.length) {
                builder.append(", ");
                builder.append(args[index++]);
            }
            builder.append(']');
        }

        return builder.toString();
    }
}
