/*
 * (c) Copyright 2023 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.serialization;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.FormatSchema;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.StreamReadCapability;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.async.NonBlockingInputFeeder;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.JacksonFeatureSet;
import com.fasterxml.jackson.core.util.RequestPayload;
import com.palantir.logsafe.exceptions.SafeIllegalStateException;
import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import javax.annotation.Nullable;

final class InstrumentedJsonFactory extends JsonFactory {

    private final ParserInstrumentation instrumentation;

    InstrumentedJsonFactory() {
        this.instrumentation = new ParserInstrumentation(getFormatName());
    }

    private InstrumentedJsonFactory(JsonFactoryBuilder builder) {
        this(builder, new ParserInstrumentation(FORMAT_NAME_JSON));
    }

    private InstrumentedJsonFactory(JsonFactoryBuilder builder, ParserInstrumentation instrumentation) {
        super(builder);
        this.instrumentation = instrumentation;
    }

    private InstrumentedJsonFactory(
            JsonFactory src, @Nullable ObjectCodec codec, ParserInstrumentation instrumentation) {
        super(src, codec);
        this.instrumentation = instrumentation;
    }

    public static JsonFactoryBuilder builder() {
        return new JsonFactoryBuilder() {
            @Override
            public JsonFactory build() {
                return new InstrumentedJsonFactory(this);
            }
        };
    }

    @Override
    public JsonFactoryBuilder rebuild() {
        return new JsonFactoryBuilder(this) {
            @Override
            public JsonFactory build() {
                return new InstrumentedJsonFactory(this, instrumentation);
            }
        };
    }

    @Override
    public JsonFactory copy() {
        return new InstrumentedJsonFactory(this, null, instrumentation);
    }

    @Override
    public String getFormatName() {
        return FORMAT_NAME_JSON;
    }

    @Override
    protected JsonParser _createParser(InputStream in, IOContext ctxt) throws IOException {
        return wrap(super._createParser(in, ctxt));
    }

    @Override
    protected JsonParser _createParser(Reader reader, IOContext ctxt) throws IOException {
        return wrap(super._createParser(reader, ctxt));
    }

    @Override
    protected JsonParser _createParser(char[] data, int offset, int len, IOContext ctxt, boolean recyclable)
            throws IOException {
        return wrap(super._createParser(data, offset, len, ctxt, recyclable));
    }

    @Override
    protected JsonParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException {
        return wrap(super._createParser(data, offset, len, ctxt));
    }

    @Override
    protected JsonParser _createParser(DataInput input, IOContext ctxt) throws IOException {
        return wrap(super._createParser(input, ctxt));
    }

    private JsonParser wrap(JsonParser input) {
        if (input == null || input instanceof InstrumentedJsonParser) {
            return input;
        }
        return new InstrumentedJsonParser(input, instrumentation);
    }

    private static final class InstrumentedJsonParser extends JsonParser {
        private final JsonParser delegate;
        private final ParserInstrumentation instrumentation;

        InstrumentedJsonParser(JsonParser delegate, ParserInstrumentation instrumentation) {
            this.delegate = delegate;
            this.instrumentation = instrumentation;
        }

        @Override
        public ObjectCodec getCodec() {
            return delegate.getCodec();
        }

        @Override
        public void setCodec(ObjectCodec oc) {
            delegate.setCodec(oc);
        }

        @Override
        public Object getInputSource() {
            return delegate.getInputSource();
        }

        @Override
        public void setRequestPayloadOnError(RequestPayload payload) {
            delegate.setRequestPayloadOnError(payload);
        }

        @Override
        public void setRequestPayloadOnError(byte[] payload, String charset) {
            delegate.setRequestPayloadOnError(payload, charset);
        }

        @Override
        public void setRequestPayloadOnError(String payload) {
            delegate.setRequestPayloadOnError(payload);
        }

        @Override
        public void setSchema(FormatSchema schema) {
            delegate.setSchema(schema);
        }

        @Override
        public FormatSchema getSchema() {
            return delegate.getSchema();
        }

        @Override
        public boolean canUseSchema(FormatSchema schema) {
            return delegate.canUseSchema(schema);
        }

        @Override
        public boolean requiresCustomCodec() {
            return delegate.requiresCustomCodec();
        }

        @Override
        public boolean canParseAsync() {
            return delegate.canParseAsync();
        }

        @Override
        public NonBlockingInputFeeder getNonBlockingInputFeeder() {
            return delegate.getNonBlockingInputFeeder();
        }

        @Override
        public JacksonFeatureSet<StreamReadCapability> getReadCapabilities() {
            return delegate.getReadCapabilities();
        }

        @Override
        public Version version() {
            return delegate.version();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public JsonStreamContext getParsingContext() {
            return delegate.getParsingContext();
        }

        @Override
        public JsonLocation currentLocation() {
            return delegate.currentLocation();
        }

        @Override
        public JsonLocation currentTokenLocation() {
            return delegate.currentTokenLocation();
        }

        @Override
        public JsonLocation getCurrentLocation() {
            return delegate.getCurrentLocation();
        }

        @Override
        public JsonLocation getTokenLocation() {
            return delegate.getTokenLocation();
        }

        @Override
        public Object currentValue() {
            return delegate.currentValue();
        }

        @Override
        public void assignCurrentValue(Object value) {
            delegate.assignCurrentValue(value);
        }

        @Override
        public Object getCurrentValue() {
            return delegate.getCurrentValue();
        }

        @Override
        public void setCurrentValue(Object value) {
            delegate.setCurrentValue(value);
        }

        @Override
        public int releaseBuffered(OutputStream out) throws IOException {
            return delegate.releaseBuffered(out);
        }

        @Override
        public int releaseBuffered(Writer writer) throws IOException {
            return delegate.releaseBuffered(writer);
        }

        @Override
        public JsonParser enable(Feature feature) {
            delegate.enable(feature);
            return this;
        }

        @Override
        public JsonParser disable(Feature feature) {
            delegate.disable(feature);
            return this;
        }

        @Override
        public JsonParser configure(Feature feature, boolean state) {
            delegate.configure(feature, state);
            return this;
        }

        @Override
        public boolean isEnabled(Feature feature) {
            return delegate.isEnabled(feature);
        }

        @Override
        public boolean isEnabled(StreamReadFeature feature) {
            return delegate.isEnabled(feature);
        }

        @Override
        public int getFeatureMask() {
            return delegate.getFeatureMask();
        }

        @Override
        @Deprecated
        public JsonParser setFeatureMask(int mask) {
            delegate.setFeatureMask(mask);
            return this;
        }

        @Override
        public JsonParser overrideStdFeatures(int values, int mask) {
            delegate.overrideStdFeatures(values, mask);
            return this;
        }

        @Override
        public int getFormatFeatures() {
            return delegate.getFormatFeatures();
        }

        @Override
        public JsonParser overrideFormatFeatures(int values, int mask) {
            delegate.overrideFormatFeatures(values, mask);
            return this;
        }

        @Override
        public JsonToken nextToken() throws IOException {
            return delegate.nextToken();
        }

        @Override
        public JsonToken nextValue() throws IOException {
            return delegate.nextValue();
        }

        @Override
        public boolean nextFieldName(SerializableString str) throws IOException {
            return delegate.nextFieldName(str);
        }

        @Override
        public String nextFieldName() throws IOException {
            return delegate.nextFieldName();
        }

        @Override
        public String nextTextValue() throws IOException {
            return instrumentation.recordStringLength(delegate.nextTextValue());
        }

        @Override
        public int nextIntValue(int defaultValue) throws IOException {
            return delegate.nextIntValue(defaultValue);
        }

        @Override
        public long nextLongValue(long defaultValue) throws IOException {
            return delegate.nextLongValue(defaultValue);
        }

        @Override
        public Boolean nextBooleanValue() throws IOException {
            return delegate.nextBooleanValue();
        }

        @Override
        public JsonParser skipChildren() throws IOException {
            delegate.skipChildren();
            return this;
        }

        @Override
        public void finishToken() throws IOException {
            delegate.finishToken();
        }

        @Override
        public JsonToken currentToken() {
            return delegate.currentToken();
        }

        @Override
        public int currentTokenId() {
            return delegate.currentTokenId();
        }

        @Override
        public JsonToken getCurrentToken() {
            return delegate.getCurrentToken();
        }

        @Override
        @Deprecated
        public int getCurrentTokenId() {
            return delegate.getCurrentTokenId();
        }

        @Override
        public boolean hasCurrentToken() {
            return delegate.hasCurrentToken();
        }

        @Override
        public boolean hasTokenId(int id) {
            return delegate.hasTokenId(id);
        }

        @Override
        public boolean hasToken(JsonToken token) {
            return delegate.hasToken(token);
        }

        @Override
        public boolean isExpectedStartArrayToken() {
            return delegate.isExpectedStartArrayToken();
        }

        @Override
        public boolean isExpectedStartObjectToken() {
            return delegate.isExpectedStartObjectToken();
        }

        @Override
        public boolean isExpectedNumberIntToken() {
            return delegate.isExpectedNumberIntToken();
        }

        @Override
        public boolean isNaN() throws IOException {
            return delegate.isNaN();
        }

        @Override
        public void clearCurrentToken() {
            delegate.clearCurrentToken();
        }

        @Override
        public JsonToken getLastClearedToken() {
            return delegate.getLastClearedToken();
        }

        @Override
        public void overrideCurrentName(String name) {
            delegate.overrideCurrentName(name);
        }

        @Override
        public String getCurrentName() throws IOException {
            return delegate.getCurrentName();
        }

        @Override
        public String currentName() throws IOException {
            return delegate.currentName();
        }

        @Override
        public String getText() throws IOException {
            return instrumentation.recordStringLength(delegate.getText());
        }

        @Override
        public int getText(Writer writer) throws IOException, UnsupportedOperationException {
            return delegate.getText(writer);
        }

        @Override
        public char[] getTextCharacters() throws IOException {
            return delegate.getTextCharacters();
        }

        @Override
        public int getTextLength() throws IOException {
            return delegate.getTextLength();
        }

        @Override
        public int getTextOffset() throws IOException {
            return delegate.getTextOffset();
        }

        @Override
        public boolean hasTextCharacters() {
            return delegate.hasTextCharacters();
        }

        @Override
        public Number getNumberValue() throws IOException {
            return delegate.getNumberValue();
        }

        @Override
        public Number getNumberValueExact() throws IOException {
            return delegate.getNumberValueExact();
        }

        @Override
        public NumberType getNumberType() throws IOException {
            return delegate.getNumberType();
        }

        @Override
        public byte getByteValue() throws IOException {
            return delegate.getByteValue();
        }

        @Override
        public short getShortValue() throws IOException {
            return delegate.getShortValue();
        }

        @Override
        public int getIntValue() throws IOException {
            return delegate.getIntValue();
        }

        @Override
        public long getLongValue() throws IOException {
            return delegate.getLongValue();
        }

        @Override
        public BigInteger getBigIntegerValue() throws IOException {
            return delegate.getBigIntegerValue();
        }

        @Override
        public float getFloatValue() throws IOException {
            return delegate.getFloatValue();
        }

        @Override
        public double getDoubleValue() throws IOException {
            return delegate.getDoubleValue();
        }

        @Override
        public BigDecimal getDecimalValue() throws IOException {
            return delegate.getDecimalValue();
        }

        @Override
        public boolean getBooleanValue() throws IOException {
            return delegate.getBooleanValue();
        }

        @Override
        public Object getEmbeddedObject() throws IOException {
            return delegate.getEmbeddedObject();
        }

        @Override
        public byte[] getBinaryValue(Base64Variant bv) throws IOException {
            return delegate.getBinaryValue(bv);
        }

        @Override
        public byte[] getBinaryValue() throws IOException {
            return delegate.getBinaryValue();
        }

        @Override
        public int readBinaryValue(OutputStream out) throws IOException {
            return delegate.readBinaryValue(out);
        }

        @Override
        public int readBinaryValue(Base64Variant bv, OutputStream out) throws IOException {
            return delegate.readBinaryValue(bv, out);
        }

        @Override
        public int getValueAsInt() throws IOException {
            return delegate.getValueAsInt();
        }

        @Override
        public int getValueAsInt(int def) throws IOException {
            return delegate.getValueAsInt(def);
        }

        @Override
        public long getValueAsLong() throws IOException {
            return delegate.getValueAsLong();
        }

        @Override
        public long getValueAsLong(long def) throws IOException {
            return delegate.getValueAsLong(def);
        }

        @Override
        public double getValueAsDouble() throws IOException {
            return delegate.getValueAsDouble();
        }

        @Override
        public double getValueAsDouble(double def) throws IOException {
            return delegate.getValueAsDouble(def);
        }

        @Override
        public boolean getValueAsBoolean() throws IOException {
            return delegate.getValueAsBoolean();
        }

        @Override
        public boolean getValueAsBoolean(boolean def) throws IOException {
            return delegate.getValueAsBoolean(def);
        }

        @Override
        public String getValueAsString() throws IOException {
            return instrumentation.recordStringLength(delegate.getValueAsString());
        }

        @Override
        public String getValueAsString(String def) throws IOException {
            return instrumentation.recordStringLength(delegate.getValueAsString(def));
        }

        @Override
        public boolean canReadObjectId() {
            return delegate.canReadObjectId();
        }

        @Override
        public boolean canReadTypeId() {
            return delegate.canReadTypeId();
        }

        @Override
        public Object getObjectId() throws IOException {
            return delegate.getObjectId();
        }

        @Override
        public Object getTypeId() throws IOException {
            return delegate.getTypeId();
        }

        @Override
        public <T> T readValueAs(Class<T> valueType) throws IOException {
            return delegate.readValueAs(valueType);
        }

        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
        public <T> T readValueAs(TypeReference<?> valueTypeRef) throws IOException {
            return delegate.readValueAs(valueTypeRef);
        }

        @Override
        public <T> Iterator<T> readValuesAs(Class<T> valueType) throws IOException {
            return delegate.readValuesAs(valueType);
        }

        @Override
        public <T> Iterator<T> readValuesAs(TypeReference<T> valueTypeRef) throws IOException {
            return delegate.readValuesAs(valueTypeRef);
        }

        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
        public <T extends TreeNode> T readValueAsTree() throws IOException {
            return delegate.readValueAsTree();
        }

        @Override
        public ObjectCodec _codec() {
            ObjectCodec codec = delegate.getCodec();
            if (codec == null) {
                throw new SafeIllegalStateException("No ObjectCodec defined for parser, needed for deserialization");
            }
            return codec;
        }
    }
}
