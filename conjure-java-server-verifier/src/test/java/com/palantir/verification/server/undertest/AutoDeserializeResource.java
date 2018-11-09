/*
 * (c) Copyright 2018 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.verification.server.undertest;

import com.palantir.conjure.verification.client.AnyExample;
import com.palantir.conjure.verification.client.AutoDeserializeService;
import com.palantir.conjure.verification.client.BearerTokenAliasExample;
import com.palantir.conjure.verification.client.BearerTokenExample;
import com.palantir.conjure.verification.client.BooleanAliasExample;
import com.palantir.conjure.verification.client.BooleanExample;
import com.palantir.conjure.verification.client.DateTimeAliasExample;
import com.palantir.conjure.verification.client.DateTimeExample;
import com.palantir.conjure.verification.client.DoubleAliasExample;
import com.palantir.conjure.verification.client.DoubleExample;
import com.palantir.conjure.verification.client.IntegerAliasExample;
import com.palantir.conjure.verification.client.IntegerExample;
import com.palantir.conjure.verification.client.KebabCaseObjectExample;
import com.palantir.conjure.verification.client.ListExample;
import com.palantir.conjure.verification.client.LongFieldNameOptionalExample;
import com.palantir.conjure.verification.client.MapExample;
import com.palantir.conjure.verification.client.OptionalBooleanExample;
import com.palantir.conjure.verification.client.OptionalExample;
import com.palantir.conjure.verification.client.OptionalIntegerExample;
import com.palantir.conjure.verification.client.RawOptionalExample;
import com.palantir.conjure.verification.client.ReferenceAliasExample;
import com.palantir.conjure.verification.client.RidAliasExample;
import com.palantir.conjure.verification.client.RidExample;
import com.palantir.conjure.verification.client.SafeLongAliasExample;
import com.palantir.conjure.verification.client.SafeLongExample;
import com.palantir.conjure.verification.client.SetDoubleExample;
import com.palantir.conjure.verification.client.SetStringExample;
import com.palantir.conjure.verification.client.SnakeCaseObjectExample;
import com.palantir.conjure.verification.client.StringAliasExample;
import com.palantir.conjure.verification.client.StringExample;
import com.palantir.conjure.verification.client.UuidAliasExample;
import com.palantir.conjure.verification.client.UuidExample;

public final class AutoDeserializeResource implements AutoDeserializeService {
    @Override
    public BearerTokenExample getBearerTokenExample(BearerTokenExample body) {
        return body;
    }

    @Override
    public BooleanExample getBooleanExample(BooleanExample body) {
        return body;
    }

    @Override
    public DateTimeExample getDateTimeExample(DateTimeExample body) {
        return body;
    }

    @Override
    public DoubleExample getDoubleExample(DoubleExample body) {
        return body;
    }

    @Override
    public IntegerExample getIntegerExample(IntegerExample body) {
        return body;
    }

    @Override
    public RidExample getRidExample(RidExample body) {
        return body;
    }

    @Override
    public SafeLongExample getSafeLongExample(SafeLongExample body) {
        return body;
    }

    @Override
    public StringExample getStringExample(StringExample body) {
        return body;
    }

    @Override
    public UuidExample getUuidExample(UuidExample body) {
        return body;
    }

    @Override
    public AnyExample getAnyExample(AnyExample body) {
        return body;
    }

    @Override
    public ListExample getListExample(ListExample body) {
        return body;
    }

    @Override
    public SetStringExample getSetStringExample(SetStringExample body) {
        return body;
    }

    @Override
    public SetDoubleExample getSetDoubleExample(SetDoubleExample body) {
        return body;
    }

    @Override
    public MapExample getMapExample(MapExample body) {
        return body;
    }

    @Override
    public OptionalExample getOptionalExample(OptionalExample body) {
        return body;
    }

    @Override
    public OptionalBooleanExample getOptionalBooleanExample(OptionalBooleanExample body) {
        return body;
    }

    @Override
    public OptionalIntegerExample getOptionalIntegerExample(OptionalIntegerExample body) {
        return body;
    }

    @Override
    public LongFieldNameOptionalExample getLongFieldNameOptionalExample(LongFieldNameOptionalExample body) {
        return body;
    }

    @Override
    public RawOptionalExample getRawOptionalExample(RawOptionalExample body) {
        return body;
    }

    @Override
    public StringAliasExample getStringAliasExample(StringAliasExample body) {
        return body;
    }

    @Override
    public DoubleAliasExample getDoubleAliasExample(DoubleAliasExample body) {
        return body;
    }

    @Override
    public IntegerAliasExample getIntegerAliasExample(IntegerAliasExample body) {
        return body;
    }

    @Override
    public BooleanAliasExample getBooleanAliasExample(BooleanAliasExample body) {
        return body;
    }

    @Override
    public SafeLongAliasExample getSafeLongAliasExample(SafeLongAliasExample body) {
        return body;
    }

    @Override
    public RidAliasExample getRidAliasExample(RidAliasExample body) {
        return body;
    }

    @Override
    public BearerTokenAliasExample getBearerTokenAliasExample(BearerTokenAliasExample body) {
        return body;
    }

    @Override
    public UuidAliasExample getUuidAliasExample(UuidAliasExample body) {
        return body;
    }

    @Override
    public ReferenceAliasExample getReferenceAliasExample(ReferenceAliasExample body) {
        return body;
    }

    @Override
    public DateTimeAliasExample getDateTimeAliasExample(DateTimeAliasExample body) {
        return body;
    }

    @Override
    public KebabCaseObjectExample getKebabCaseObjectExample(KebabCaseObjectExample body) {
        return body;
    }

    @Override
    public SnakeCaseObjectExample getSnakeCaseObjectExample(SnakeCaseObjectExample body) {
        return body;
    }
}
