package com.palantir.conjure.verification.client;

import com.palantir.conjure.java.lib.internal.ClientEndpoint;
import com.palantir.conjure.verification.types.AnyExample;
import com.palantir.conjure.verification.types.BearerTokenAliasExample;
import com.palantir.conjure.verification.types.BearerTokenExample;
import com.palantir.conjure.verification.types.BinaryExample;
import com.palantir.conjure.verification.types.BooleanAliasExample;
import com.palantir.conjure.verification.types.BooleanExample;
import com.palantir.conjure.verification.types.DateTimeAliasExample;
import com.palantir.conjure.verification.types.DateTimeExample;
import com.palantir.conjure.verification.types.DoubleAliasExample;
import com.palantir.conjure.verification.types.DoubleExample;
import com.palantir.conjure.verification.types.EnumExample;
import com.palantir.conjure.verification.types.IntegerAliasExample;
import com.palantir.conjure.verification.types.IntegerExample;
import com.palantir.conjure.verification.types.KebabCaseObjectExample;
import com.palantir.conjure.verification.types.ListAnyAliasExample;
import com.palantir.conjure.verification.types.ListBearerTokenAliasExample;
import com.palantir.conjure.verification.types.ListBinaryAliasExample;
import com.palantir.conjure.verification.types.ListBooleanAliasExample;
import com.palantir.conjure.verification.types.ListDateTimeAliasExample;
import com.palantir.conjure.verification.types.ListDoubleAliasExample;
import com.palantir.conjure.verification.types.ListExample;
import com.palantir.conjure.verification.types.ListIntegerAliasExample;
import com.palantir.conjure.verification.types.ListOptionalAnyAliasExample;
import com.palantir.conjure.verification.types.ListRidAliasExample;
import com.palantir.conjure.verification.types.ListSafeLongAliasExample;
import com.palantir.conjure.verification.types.ListStringAliasExample;
import com.palantir.conjure.verification.types.ListUuidAliasExample;
import com.palantir.conjure.verification.types.LongFieldNameOptionalExample;
import com.palantir.conjure.verification.types.MapBearerTokenAliasExample;
import com.palantir.conjure.verification.types.MapBinaryAliasExample;
import com.palantir.conjure.verification.types.MapBooleanAliasExample;
import com.palantir.conjure.verification.types.MapDateTimeAliasExample;
import com.palantir.conjure.verification.types.MapDoubleAliasExample;
import com.palantir.conjure.verification.types.MapEnumExampleAlias;
import com.palantir.conjure.verification.types.MapExample;
import com.palantir.conjure.verification.types.MapIntegerAliasExample;
import com.palantir.conjure.verification.types.MapRidAliasExample;
import com.palantir.conjure.verification.types.MapSafeLongAliasExample;
import com.palantir.conjure.verification.types.MapStringAliasExample;
import com.palantir.conjure.verification.types.MapUuidAliasExample;
import com.palantir.conjure.verification.types.OptionalAnyAliasExample;
import com.palantir.conjure.verification.types.OptionalBearerTokenAliasExample;
import com.palantir.conjure.verification.types.OptionalBooleanAliasExample;
import com.palantir.conjure.verification.types.OptionalBooleanExample;
import com.palantir.conjure.verification.types.OptionalDateTimeAliasExample;
import com.palantir.conjure.verification.types.OptionalDoubleAliasExample;
import com.palantir.conjure.verification.types.OptionalExample;
import com.palantir.conjure.verification.types.OptionalIntegerAliasExample;
import com.palantir.conjure.verification.types.OptionalIntegerExample;
import com.palantir.conjure.verification.types.OptionalRidAliasExample;
import com.palantir.conjure.verification.types.OptionalSafeLongAliasExample;
import com.palantir.conjure.verification.types.OptionalStringAliasExample;
import com.palantir.conjure.verification.types.OptionalUuidAliasExample;
import com.palantir.conjure.verification.types.RawOptionalExample;
import com.palantir.conjure.verification.types.ReferenceAliasExample;
import com.palantir.conjure.verification.types.RidAliasExample;
import com.palantir.conjure.verification.types.RidExample;
import com.palantir.conjure.verification.types.SafeLongAliasExample;
import com.palantir.conjure.verification.types.SafeLongExample;
import com.palantir.conjure.verification.types.SetAnyAliasExample;
import com.palantir.conjure.verification.types.SetBearerTokenAliasExample;
import com.palantir.conjure.verification.types.SetBinaryAliasExample;
import com.palantir.conjure.verification.types.SetBooleanAliasExample;
import com.palantir.conjure.verification.types.SetDateTimeAliasExample;
import com.palantir.conjure.verification.types.SetDoubleAliasExample;
import com.palantir.conjure.verification.types.SetDoubleExample;
import com.palantir.conjure.verification.types.SetIntegerAliasExample;
import com.palantir.conjure.verification.types.SetOptionalAnyAliasExample;
import com.palantir.conjure.verification.types.SetRidAliasExample;
import com.palantir.conjure.verification.types.SetSafeLongAliasExample;
import com.palantir.conjure.verification.types.SetStringAliasExample;
import com.palantir.conjure.verification.types.SetStringExample;
import com.palantir.conjure.verification.types.SetUuidAliasExample;
import com.palantir.conjure.verification.types.SnakeCaseObjectExample;
import com.palantir.conjure.verification.types.StringAliasExample;
import com.palantir.conjure.verification.types.StringExample;
import com.palantir.conjure.verification.types.UuidAliasExample;
import com.palantir.conjure.verification.types.UuidExample;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.InputStream;
import javax.annotation.processing.Generated;

@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/")
@Generated("com.palantir.conjure.java.services.JerseyServiceGenerator")
public interface AutoDeserializeService {
    @POST
    @Path("body/getBearerTokenExample")
    @ClientEndpoint(method = "POST", path = "/body/getBearerTokenExample")
    BearerTokenExample getBearerTokenExample(BearerTokenExample body);

    @POST
    @Path("body/getBinaryExample")
    @ClientEndpoint(method = "POST", path = "/body/getBinaryExample")
    BinaryExample getBinaryExample(BinaryExample body);

    @POST
    @Path("body/getBooleanExample")
    @ClientEndpoint(method = "POST", path = "/body/getBooleanExample")
    BooleanExample getBooleanExample(BooleanExample body);

    @POST
    @Path("body/getDateTimeExample")
    @ClientEndpoint(method = "POST", path = "/body/getDateTimeExample")
    DateTimeExample getDateTimeExample(DateTimeExample body);

    @POST
    @Path("body/getDoubleExample")
    @ClientEndpoint(method = "POST", path = "/body/getDoubleExample")
    DoubleExample getDoubleExample(DoubleExample body);

    @POST
    @Path("body/getIntegerExample")
    @ClientEndpoint(method = "POST", path = "/body/getIntegerExample")
    IntegerExample getIntegerExample(IntegerExample body);

    @POST
    @Path("body/getRidExample")
    @ClientEndpoint(method = "POST", path = "/body/getRidExample")
    RidExample getRidExample(RidExample body);

    @POST
    @Path("body/getSafeLongExample")
    @ClientEndpoint(method = "POST", path = "/body/getSafeLongExample")
    SafeLongExample getSafeLongExample(SafeLongExample body);

    @POST
    @Path("body/getStringExample")
    @ClientEndpoint(method = "POST", path = "/body/getStringExample")
    StringExample getStringExample(StringExample body);

    @POST
    @Path("body/getUuidExample")
    @ClientEndpoint(method = "POST", path = "/body/getUuidExample")
    UuidExample getUuidExample(UuidExample body);

    @POST
    @Path("body/getAnyExample")
    @ClientEndpoint(method = "POST", path = "/body/getAnyExample")
    AnyExample getAnyExample(AnyExample body);

    @POST
    @Path("body/getEnumExample")
    @ClientEndpoint(method = "POST", path = "/body/getEnumExample")
    EnumExample getEnumExample(EnumExample body);

    @POST
    @Path("body/getListExample")
    @ClientEndpoint(method = "POST", path = "/body/getListExample")
    ListExample getListExample(ListExample body);

    @POST
    @Path("body/getSetStringExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetStringExample")
    SetStringExample getSetStringExample(SetStringExample body);

    @POST
    @Path("body/getSetDoubleExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetDoubleExample")
    SetDoubleExample getSetDoubleExample(SetDoubleExample body);

    @POST
    @Path("body/getMapExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapExample")
    MapExample getMapExample(MapExample body);

    @POST
    @Path("body/getOptionalExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalExample")
    OptionalExample getOptionalExample(OptionalExample body);

    @POST
    @Path("body/getOptionalBooleanExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalBooleanExample")
    OptionalBooleanExample getOptionalBooleanExample(OptionalBooleanExample body);

    @POST
    @Path("body/getOptionalIntegerExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalIntegerExample")
    OptionalIntegerExample getOptionalIntegerExample(OptionalIntegerExample body);

    @POST
    @Path("body/getLongFieldNameOptionalExample")
    @ClientEndpoint(method = "POST", path = "/body/getLongFieldNameOptionalExample")
    LongFieldNameOptionalExample getLongFieldNameOptionalExample(LongFieldNameOptionalExample body);

    @POST
    @Path("body/getRawOptionalExample")
    @ClientEndpoint(method = "POST", path = "/body/getRawOptionalExample")
    RawOptionalExample getRawOptionalExample(RawOptionalExample body);

    @POST
    @Path("body/getStringAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getStringAliasExample")
    StringAliasExample getStringAliasExample(StringAliasExample body);

    @POST
    @Path("body/getDoubleAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getDoubleAliasExample")
    DoubleAliasExample getDoubleAliasExample(DoubleAliasExample body);

    @POST
    @Path("body/getIntegerAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getIntegerAliasExample")
    IntegerAliasExample getIntegerAliasExample(IntegerAliasExample body);

    @POST
    @Path("body/getBooleanAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getBooleanAliasExample")
    BooleanAliasExample getBooleanAliasExample(BooleanAliasExample body);

    @POST
    @Path("body/getSafeLongAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSafeLongAliasExample")
    SafeLongAliasExample getSafeLongAliasExample(SafeLongAliasExample body);

    @POST
    @Path("body/getRidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getRidAliasExample")
    RidAliasExample getRidAliasExample(RidAliasExample body);

    @POST
    @Path("body/getBearerTokenAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getBearerTokenAliasExample")
    BearerTokenAliasExample getBearerTokenAliasExample(BearerTokenAliasExample body);

    @POST
    @Path("body/getUuidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getUuidAliasExample")
    UuidAliasExample getUuidAliasExample(UuidAliasExample body);

    @POST
    @Path("body/getReferenceAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getReferenceAliasExample")
    ReferenceAliasExample getReferenceAliasExample(ReferenceAliasExample body);

    @POST
    @Path("body/getDateTimeAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getDateTimeAliasExample")
    DateTimeAliasExample getDateTimeAliasExample(DateTimeAliasExample body);

    @POST
    @Path("body/getBinaryAliasExample")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @ClientEndpoint(method = "POST", path = "/body/getBinaryAliasExample")
    StreamingOutput getBinaryAliasExample(InputStream body);

    @POST
    @Path("body/getKebabCaseObjectExample")
    @ClientEndpoint(method = "POST", path = "/body/getKebabCaseObjectExample")
    KebabCaseObjectExample getKebabCaseObjectExample(KebabCaseObjectExample body);

    @POST
    @Path("body/getSnakeCaseObjectExample")
    @ClientEndpoint(method = "POST", path = "/body/getSnakeCaseObjectExample")
    SnakeCaseObjectExample getSnakeCaseObjectExample(SnakeCaseObjectExample body);

    @POST
    @Path("body/getOptionalBearerTokenAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalBearerTokenAliasExample")
    OptionalBearerTokenAliasExample getOptionalBearerTokenAliasExample(OptionalBearerTokenAliasExample body);

    @POST
    @Path("body/getOptionalBooleanAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalBooleanAliasExample")
    OptionalBooleanAliasExample getOptionalBooleanAliasExample(OptionalBooleanAliasExample body);

    @POST
    @Path("body/getOptionalDateTimeAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalDateTimeAliasExample")
    OptionalDateTimeAliasExample getOptionalDateTimeAliasExample(OptionalDateTimeAliasExample body);

    @POST
    @Path("body/getOptionalDoubleAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalDoubleAliasExample")
    OptionalDoubleAliasExample getOptionalDoubleAliasExample(OptionalDoubleAliasExample body);

    @POST
    @Path("body/getOptionalIntegerAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalIntegerAliasExample")
    OptionalIntegerAliasExample getOptionalIntegerAliasExample(OptionalIntegerAliasExample body);

    @POST
    @Path("body/getOptionalRidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalRidAliasExample")
    OptionalRidAliasExample getOptionalRidAliasExample(OptionalRidAliasExample body);

    @POST
    @Path("body/getOptionalSafeLongAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalSafeLongAliasExample")
    OptionalSafeLongAliasExample getOptionalSafeLongAliasExample(OptionalSafeLongAliasExample body);

    @POST
    @Path("body/getOptionalStringAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalStringAliasExample")
    OptionalStringAliasExample getOptionalStringAliasExample(OptionalStringAliasExample body);

    @POST
    @Path("body/getOptionalUuidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalUuidAliasExample")
    OptionalUuidAliasExample getOptionalUuidAliasExample(OptionalUuidAliasExample body);

    @POST
    @Path("body/getOptionalAnyAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getOptionalAnyAliasExample")
    OptionalAnyAliasExample getOptionalAnyAliasExample(OptionalAnyAliasExample body);

    @POST
    @Path("body/getListBearerTokenAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListBearerTokenAliasExample")
    ListBearerTokenAliasExample getListBearerTokenAliasExample(ListBearerTokenAliasExample body);

    @POST
    @Path("body/getListBinaryAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListBinaryAliasExample")
    ListBinaryAliasExample getListBinaryAliasExample(ListBinaryAliasExample body);

    @POST
    @Path("body/getListBooleanAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListBooleanAliasExample")
    ListBooleanAliasExample getListBooleanAliasExample(ListBooleanAliasExample body);

    @POST
    @Path("body/getListDateTimeAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListDateTimeAliasExample")
    ListDateTimeAliasExample getListDateTimeAliasExample(ListDateTimeAliasExample body);

    @POST
    @Path("body/getListDoubleAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListDoubleAliasExample")
    ListDoubleAliasExample getListDoubleAliasExample(ListDoubleAliasExample body);

    @POST
    @Path("body/getListIntegerAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListIntegerAliasExample")
    ListIntegerAliasExample getListIntegerAliasExample(ListIntegerAliasExample body);

    @POST
    @Path("body/getListRidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListRidAliasExample")
    ListRidAliasExample getListRidAliasExample(ListRidAliasExample body);

    @POST
    @Path("body/getListSafeLongAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListSafeLongAliasExample")
    ListSafeLongAliasExample getListSafeLongAliasExample(ListSafeLongAliasExample body);

    @POST
    @Path("body/getListStringAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListStringAliasExample")
    ListStringAliasExample getListStringAliasExample(ListStringAliasExample body);

    @POST
    @Path("body/getListUuidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListUuidAliasExample")
    ListUuidAliasExample getListUuidAliasExample(ListUuidAliasExample body);

    @POST
    @Path("body/getListAnyAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListAnyAliasExample")
    ListAnyAliasExample getListAnyAliasExample(ListAnyAliasExample body);

    @POST
    @Path("body/getListOptionalAnyAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getListOptionalAnyAliasExample")
    ListOptionalAnyAliasExample getListOptionalAnyAliasExample(ListOptionalAnyAliasExample body);

    @POST
    @Path("body/getSetBearerTokenAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetBearerTokenAliasExample")
    SetBearerTokenAliasExample getSetBearerTokenAliasExample(SetBearerTokenAliasExample body);

    @POST
    @Path("body/getSetBinaryAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetBinaryAliasExample")
    SetBinaryAliasExample getSetBinaryAliasExample(SetBinaryAliasExample body);

    @POST
    @Path("body/getSetBooleanAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetBooleanAliasExample")
    SetBooleanAliasExample getSetBooleanAliasExample(SetBooleanAliasExample body);

    @POST
    @Path("body/getSetDateTimeAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetDateTimeAliasExample")
    SetDateTimeAliasExample getSetDateTimeAliasExample(SetDateTimeAliasExample body);

    @POST
    @Path("body/getSetDoubleAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetDoubleAliasExample")
    SetDoubleAliasExample getSetDoubleAliasExample(SetDoubleAliasExample body);

    @POST
    @Path("body/getSetIntegerAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetIntegerAliasExample")
    SetIntegerAliasExample getSetIntegerAliasExample(SetIntegerAliasExample body);

    @POST
    @Path("body/getSetRidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetRidAliasExample")
    SetRidAliasExample getSetRidAliasExample(SetRidAliasExample body);

    @POST
    @Path("body/getSetSafeLongAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetSafeLongAliasExample")
    SetSafeLongAliasExample getSetSafeLongAliasExample(SetSafeLongAliasExample body);

    @POST
    @Path("body/getSetStringAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetStringAliasExample")
    SetStringAliasExample getSetStringAliasExample(SetStringAliasExample body);

    @POST
    @Path("body/getSetUuidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetUuidAliasExample")
    SetUuidAliasExample getSetUuidAliasExample(SetUuidAliasExample body);

    @POST
    @Path("body/getSetAnyAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetAnyAliasExample")
    SetAnyAliasExample getSetAnyAliasExample(SetAnyAliasExample body);

    @POST
    @Path("body/getSetOptionalAnyAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getSetOptionalAnyAliasExample")
    SetOptionalAnyAliasExample getSetOptionalAnyAliasExample(SetOptionalAnyAliasExample body);

    @POST
    @Path("body/getMapBearerTokenAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapBearerTokenAliasExample")
    MapBearerTokenAliasExample getMapBearerTokenAliasExample(MapBearerTokenAliasExample body);

    @POST
    @Path("body/getMapBinaryAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapBinaryAliasExample")
    MapBinaryAliasExample getMapBinaryAliasExample(MapBinaryAliasExample body);

    @POST
    @Path("body/getMapBooleanAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapBooleanAliasExample")
    MapBooleanAliasExample getMapBooleanAliasExample(MapBooleanAliasExample body);

    @POST
    @Path("body/getMapDateTimeAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapDateTimeAliasExample")
    MapDateTimeAliasExample getMapDateTimeAliasExample(MapDateTimeAliasExample body);

    @POST
    @Path("body/getMapDoubleAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapDoubleAliasExample")
    MapDoubleAliasExample getMapDoubleAliasExample(MapDoubleAliasExample body);

    @POST
    @Path("body/getMapIntegerAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapIntegerAliasExample")
    MapIntegerAliasExample getMapIntegerAliasExample(MapIntegerAliasExample body);

    @POST
    @Path("body/getMapRidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapRidAliasExample")
    MapRidAliasExample getMapRidAliasExample(MapRidAliasExample body);

    @POST
    @Path("body/getMapSafeLongAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapSafeLongAliasExample")
    MapSafeLongAliasExample getMapSafeLongAliasExample(MapSafeLongAliasExample body);

    @POST
    @Path("body/getMapStringAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapStringAliasExample")
    MapStringAliasExample getMapStringAliasExample(MapStringAliasExample body);

    @POST
    @Path("body/getMapUuidAliasExample")
    @ClientEndpoint(method = "POST", path = "/body/getMapUuidAliasExample")
    MapUuidAliasExample getMapUuidAliasExample(MapUuidAliasExample body);

    @POST
    @Path("body/getMapEnumExampleAlias")
    @ClientEndpoint(method = "POST", path = "/body/getMapEnumExampleAlias")
    MapEnumExampleAlias getMapEnumExampleAlias(MapEnumExampleAlias body);
}
