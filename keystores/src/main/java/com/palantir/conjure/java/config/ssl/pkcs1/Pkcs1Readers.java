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

package com.palantir.conjure.java.config.ssl.pkcs1;

import com.google.common.base.Suppliers;
import com.google.common.collect.Iterators;
import com.palantir.logsafe.Preconditions;
import java.util.ServiceLoader;
import java.util.function.Supplier;

public final class Pkcs1Readers {

    private static final ServiceLoader<Pkcs1Reader> PKCS1_READER_LOADER = ServiceLoader.load(Pkcs1Reader.class);
    private static final Supplier<Pkcs1Reader> PKCS1_READER_SUPPLIER = Suppliers.memoize(() -> {
        Pkcs1Reader reader = Iterators.getNext(PKCS1_READER_LOADER.iterator(), null);

        Preconditions.checkState(reader != null, "No Pkcs1Reader services were present. Ensure that a Pkcs1Reader "
                + "with a properly configured META-INF/services/ entry is present on the classpath.");

        return reader;
    });

    public static Pkcs1Reader getInstance() {
        return PKCS1_READER_SUPPLIER.get();
    }

    private Pkcs1Readers() {}
}
