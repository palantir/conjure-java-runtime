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

package com.palantir.conjure.java.tracing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.google.common.collect.Lists;
import com.palantir.conjure.java.api.tracing.Span;
import com.palantir.logsafe.UnsafeArg;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import org.immutables.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link AsyncSpanObserver asynchronous SpanObserver} that logs every span to a configurable SLF4J {@link Logger}
 * with log-level {@link Logger#info INFO}. Logging is performed asynchronously on a given executor service.
 */
public final class AsyncSlf4jSpanObserver extends AsyncSpanObserver {
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new GuavaModule())
            .registerModule(new Jdk8Module().configureAbsentsAsNulls(true))
            .registerModule(new AfterburnerModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .disable(DeserializationFeature.WRAP_EXCEPTIONS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Logger logger;
    private final ZipkinCompatEndpoint endpoint;

    @JsonSerialize(as = ImmutableZipkinCompatSpan.class)
    @Value.Immutable
    @Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
    abstract static class ZipkinCompatSpan {

        abstract String getTraceId();
        abstract String getId();
        abstract String getName();
        abstract Optional<String> getParentId();
        abstract long getTimestamp();
        abstract long getDuration();
        abstract List<ZipkinCompatAnnotation> annotations();
        abstract List<ZipkinCompatBinaryAnnotation> binaryAnnotations();

        static ZipkinCompatSpan fromSpan(Span span, ZipkinCompatEndpoint endpoint) {
            return ImmutableZipkinCompatSpan.builder()
                    .traceId(span.getTraceId())
                    .id(span.getSpanId())
                    .name(span.getOperation())
                    .parentId(span.getParentSpanId())
                    .timestamp(span.getStartTimeMicroSeconds())
                    .duration(nanoToMicro(span.getDurationNanoSeconds()))  // Zipkin-durations are micro-seconds, round
                    .addAllAnnotations(spanTypeToZipkinAnnotations(span, endpoint))
                    .addAllBinaryAnnotations(spanMetadataToZipkinBinaryAnnotations(span, endpoint))
                    .build();
        }

        private static Iterable<? extends ZipkinCompatAnnotation> spanTypeToZipkinAnnotations(
                Span span, ZipkinCompatEndpoint endpoint) {

            List<ZipkinCompatAnnotation> annotations = Lists.newArrayListWithCapacity(2);
            switch (span.type()) {
                case CLIENT_OUTGOING:
                    annotations.add(ZipkinCompatAnnotation.of(span.getStartTimeMicroSeconds(), "cs", endpoint));
                    annotations.add(ZipkinCompatAnnotation.of(
                            span.getStartTimeMicroSeconds() + nanoToMicro(span.getDurationNanoSeconds()),
                            "cr",
                            endpoint));
                    break;
                case SERVER_INCOMING:
                    annotations.add(ZipkinCompatAnnotation.of(span.getStartTimeMicroSeconds(), "sr", endpoint));
                    annotations.add(ZipkinCompatAnnotation.of(
                            span.getStartTimeMicroSeconds() + nanoToMicro(span.getDurationNanoSeconds()),
                            "ss",
                            endpoint));
                    break;
                case LOCAL:
                    annotations.add(ZipkinCompatAnnotation.of(span.getStartTimeMicroSeconds(), "lc", endpoint));
                    break;
                default:
                    throw new RuntimeException("Unhandled SpanType: " + span.type());
            }
            return annotations;
        }

        private static Iterable<? extends ZipkinCompatBinaryAnnotation> spanMetadataToZipkinBinaryAnnotations(
                Span span, ZipkinCompatEndpoint endpoint) {
            List<ZipkinCompatBinaryAnnotation> binaryAnnotations = Lists.newArrayList();
            for (Map.Entry<String, String> entry : span.getMetadata().entrySet()) {
                binaryAnnotations.add(ZipkinCompatBinaryAnnotation.of(entry.getKey(), entry.getValue(), endpoint));
            }
            return binaryAnnotations;
        }

        static long nanoToMicro(long nano) {
            return (nano + 1000) / 1000L;
        }

        String toJson() {
            try {
                return mapper.writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return "UNSERIALIZABLE: " + toString();
            }
        }
    }

    @JsonSerialize(as = ImmutableZipkinCompatAnnotation.class)
    @Value.Immutable
    @Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
    abstract static class ZipkinCompatAnnotation {
        abstract long timestamp(); // epoch microseconds
        abstract String value();
        abstract ZipkinCompatEndpoint endpoint();

        static ZipkinCompatAnnotation of(long timestamp, String value, ZipkinCompatEndpoint endpoint) {
            return ImmutableZipkinCompatAnnotation.builder()
                    .timestamp(timestamp)
                    .value(value)
                    .endpoint(endpoint)
                    .build();
        }
    }

    @JsonSerialize(as = ImmutableZipkinCompatBinaryAnnotation.class)
    @Value.Immutable
    @Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
    abstract static class ZipkinCompatBinaryAnnotation {

        abstract String key();
        abstract String value();
        abstract ZipkinCompatEndpoint endpoint();

        static ZipkinCompatBinaryAnnotation of(String key, String value, ZipkinCompatEndpoint endpoint) {
            return ImmutableZipkinCompatBinaryAnnotation.builder()
                    .key(key)
                    .value(value)
                    .endpoint(endpoint)
                    .build();
        }
    }

    @JsonSerialize(as = ImmutableZipkinCompatEndpoint.class)
    @Value.Immutable
    @Value.Style(visibility = Value.Style.ImplementationVisibility.PACKAGE)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    abstract static class ZipkinCompatEndpoint {
        abstract String serviceName();
        abstract Optional<String> ipv4();
        abstract Optional<String> ipv6();
        // port may be omitted
    }

    private AsyncSlf4jSpanObserver(String serviceName, InetAddress ip, Logger logger, ExecutorService executorService) {
        super(executorService);

        ImmutableZipkinCompatEndpoint.Builder endpointBuilder = ImmutableZipkinCompatEndpoint.builder()
                .serviceName(serviceName);
        if (ip instanceof Inet4Address) {
            endpointBuilder.ipv4(ip.getHostAddress());
        } else if (ip instanceof Inet6Address) {
            endpointBuilder.ipv6(ip.getHostAddress());
        }
        this.endpoint = endpointBuilder.build();

        this.logger = logger;
    }

    public static AsyncSlf4jSpanObserver of(String serviceName, ExecutorService executorService) {
        return new AsyncSlf4jSpanObserver(serviceName, InetAddressSupplier.INSTANCE.get(),
                LoggerFactory.getLogger(AsyncSlf4jSpanObserver.class), executorService);
    }

    public static AsyncSlf4jSpanObserver of(
            String serviceName, InetAddress ip, Logger logger, ExecutorService executorService) {
        return new AsyncSlf4jSpanObserver(serviceName, ip, logger, executorService);
    }

    @Override
    public void doConsume(Span span) {
        if (logger.isTraceEnabled()) {
            logger.trace("{}", UnsafeArg.of("span", ZipkinCompatSpan.fromSpan(span, endpoint).toJson()));
        }
    }
}
