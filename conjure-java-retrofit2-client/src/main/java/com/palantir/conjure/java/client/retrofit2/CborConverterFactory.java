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

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import com.google.common.net.HttpHeaders;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Objects;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Retrofit;
import retrofit2.http.Headers;

public final class CborConverterFactory extends Converter.Factory {

    private static final MediaType CBOR_MIME_TYPE = MediaType.parse("application/cbor");

    private final Converter.Factory delegate;
    private final CBORMapper cborMapper;

    CborConverterFactory(Converter.Factory delegate, CBORMapper cborMapper) {
        this.delegate = delegate;
        this.cborMapper = cborMapper;
    }

    @Override
    public Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
        // given we don't know how to convert the response until we check the Content-Type, we construct a delegate
        // converter for when the response is not application/cbor.
        Converter<ResponseBody, ?> delegateConverter = delegate.responseBodyConverter(type, annotations, retrofit);
        JavaType javaType = cborMapper.getTypeFactory().constructType(type);
        ObjectReader objectReader = cborMapper.readerFor(javaType);
        return new CborResponseBodyConverter<>(objectReader, delegateConverter);
    }

    @Override
    public Converter<?, RequestBody> requestBodyConverter(
            Type type, Annotation[] parameterAnnotations, Annotation[] methodAnnotations, Retrofit retrofit) {
        if (contentTypeIsCbor(methodAnnotations)) {
            return new CborRequestBodyConverter<>(cborMapper.writer());
        } else {
            return delegate.requestBodyConverter(type, parameterAnnotations, methodAnnotations, retrofit);
        }
    }

    private static boolean contentTypeIsCbor(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Headers) {
                Headers headers = (Headers) annotation;
                for (String header : headers.value()) {
                    int index = header.indexOf(":");
                    if (index == -1) {
                        continue;
                    }

                    String headerType = header.substring(0, index);
                    if (!headerType.equals(HttpHeaders.CONTENT_TYPE)) {
                        continue;
                    }

                    String headerValue = header.substring(index + 1).trim();
                    MediaType mediaType = MediaType.parse(headerValue);
                    if (Objects.equals(mediaType, CBOR_MIME_TYPE)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    static final class CborRequestBodyConverter<T> implements Converter<T, RequestBody> {

        private final ObjectWriter cborObjectWriter;

        CborRequestBodyConverter(ObjectWriter cborObjectWriter) {
            this.cborObjectWriter = cborObjectWriter;
        }

        @Override
        public RequestBody convert(T value) throws IOException {
            byte[] bytes = cborObjectWriter.writeValueAsBytes(value);
            return RequestBody.create(CBOR_MIME_TYPE, bytes);
        }
    }

    static final class CborResponseBodyConverter<T> implements Converter<ResponseBody, T> {

        private final ObjectReader cborObjectReader;
        private final Converter<ResponseBody, T> delegate;

        CborResponseBodyConverter(ObjectReader cborObjectReader, Converter<ResponseBody, T> delegate) {
            this.cborObjectReader = cborObjectReader;
            this.delegate = delegate;
        }

        @Override
        public T convert(ResponseBody value) throws IOException {
            if (value.contentType() == null || !value.contentType().equals(CBOR_MIME_TYPE)) {
                return delegate.convert(value);
            }

            try {
                return cborObjectReader.readValue(value.byteStream());
            } finally {
                value.close();
            }
        }
    }
}
