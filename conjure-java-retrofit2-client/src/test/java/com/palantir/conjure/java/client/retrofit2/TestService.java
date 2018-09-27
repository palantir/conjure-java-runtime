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

package com.palantir.conjure.java.client.retrofit2;

import com.google.common.util.concurrent.ListenableFuture;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Headers;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface TestService {
    @GET(".")
    Call<String> get();

    @GET("getGuavaOptionalString/{pathString}/")
    Call<com.google.common.base.Optional<String>> getGuavaOptionalString(
            @Path("pathString") com.google.common.base.Optional<String> pathString,
            @Query("queryString") com.google.common.base.Optional<String> queryString);

    @GET("getJava8OptionalString/{pathString}/")
    Call<java.util.Optional<String>> getJava8OptionalString(
            @Path("pathString") java.util.Optional<String> pathString,
            @Query("queryString") java.util.Optional<String> queryString);

    @POST("getComplexGuavaType")
    Call<com.google.common.base.Optional<LocalDate>> getComplexGuavaType(
            @Body com.google.common.base.Optional<LocalDate> date);

    @POST("getComplexJavaType")
    Call<java.util.Optional<LocalDate>> getComplexJava8Type(@Body java.util.Optional<LocalDate> date);

    @GET("relative")
    Call<String> getRelative();

    @GET("getComplexCborType")
    @Headers("Accepts: application/cbor")
    Call<java.util.Optional<LocalDate>> getComplexCborType();

    @POST("makeCborRequest")
    @Headers("Content-Type: application/cbor")
    Call<Void> makeCborRequest(@Body LocalDate localDate);

    @POST("makeCompletableFutureRequest")
    CompletableFuture<String> makeCompletableFutureRequest();

    @POST("makeListenableFutureRequest")
    ListenableFuture<String> makeListenableFutureRequest();
}
