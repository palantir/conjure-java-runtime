/*
 * Copyright 2017 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting2.ext.jackson.discoverable;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

@SuppressWarnings("checkstyle:EmptyLineSeparator")
public final class MetaInfBasedSubtypeFinderTest {

    @Test
    public void findSubtypes() throws Exception {
        MetaInfBasedSubtypeFinder finder = new MetaInfBasedSubtypeFinder();

        assertThat(finder.findSubtypes(Discoverable.class))
                .as("F is part of the hierarchy, but was not registered in META-INF/services, "
                        + "everything else should be there, no matter how deep in the hierarchy")
                .doesNotContain(F.class)
                .contains(A.class, B.class, C.class, D.class, E.class, G.class);
    }

    private static class A implements Discoverable { }
    private static class G implements Discoverable { }
    private static class B extends A { }
    private static class C extends B { }
    private static class D extends C { }
    private static class E extends B { }
    private static class F extends C { } // But not in META-INF/services
}
