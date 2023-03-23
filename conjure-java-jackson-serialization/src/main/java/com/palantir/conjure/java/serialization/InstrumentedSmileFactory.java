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

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.sym.ByteQuadsCanonicalizer;
import com.fasterxml.jackson.dataformat.smile.SmileConstants;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import com.fasterxml.jackson.dataformat.smile.SmileFactoryBuilder;
import com.fasterxml.jackson.dataformat.smile.SmileParser;
import com.fasterxml.jackson.dataformat.smile.SmileParserBootstrapper;
import java.io.IOException;
import java.io.InputStream;
import javax.annotation.Nullable;

final class InstrumentedSmileFactory extends SmileFactory {

    private final ParserInstrumentation instrumentation;

    InstrumentedSmileFactory() {
        instrumentation = new ParserInstrumentation(getFormatName());
    }

    private InstrumentedSmileFactory(SmileFactoryBuilder builder) {
        this(builder, new ParserInstrumentation(FORMAT_NAME_SMILE));
    }

    private InstrumentedSmileFactory(SmileFactoryBuilder builder, ParserInstrumentation instrumentation) {
        super(builder);
        this.instrumentation = instrumentation;
    }

    private InstrumentedSmileFactory(
            SmileFactory src, @Nullable ObjectCodec codec, ParserInstrumentation instrumentation) {
        super(src, codec);
        this.instrumentation = instrumentation;
    }

    public static SmileFactoryBuilder builder() {
        return new SmileFactoryBuilder() {
            @Override
            public SmileFactory build() {
                return new InstrumentedSmileFactory(this);
            }
        };
    }

    @Override
    public SmileFactoryBuilder rebuild() {
        return new SmileFactoryBuilder(this) {
            @Override
            public SmileFactory build() {
                return new InstrumentedSmileFactory(this, instrumentation);
            }
        };
    }

    @Override
    public SmileFactory copy() {
        return new InstrumentedSmileFactory(this, null, instrumentation);
    }

    @Override
    protected SmileParser _createParser(InputStream in, IOContext ctxt) throws IOException {
        SmileParserBootstrapper bootstrapper = new InstrumentedSmileParserBootstrapper(ctxt, in, instrumentation);
        return bootstrapper.constructParser(
                _factoryFeatures, _parserFeatures, _smileParserFeatures, _objectCodec, _byteSymbolCanonicalizer);
    }

    @Override
    protected SmileParser _createParser(byte[] data, int offset, int len, IOContext ctxt) throws IOException {
        return new InstrumentedSmileParserBootstrapper(ctxt, data, offset, len, instrumentation)
                .constructParser(
                        _factoryFeatures,
                        _parserFeatures,
                        _smileParserFeatures,
                        _objectCodec,
                        _byteSymbolCanonicalizer);
    }

    private static final class InstrumentedSmileParserBootstrapper extends SmileParserBootstrapper {

        private final ParserInstrumentation instrumentation;

        InstrumentedSmileParserBootstrapper(IOContext ctxt, InputStream in, ParserInstrumentation instrumentation) {
            super(ctxt, in);
            this.instrumentation = instrumentation;
        }

        InstrumentedSmileParserBootstrapper(
                IOContext ctxt,
                byte[] inputBuffer,
                int inputStart,
                int inputLen,
                ParserInstrumentation instrumentation) {
            super(ctxt, inputBuffer, inputStart, inputLen);
            this.instrumentation = instrumentation;
        }

        /**
         * This method is copied from the superclass, updated to instantiate an instrumented smile factory
         * instead of the default.
         * Original implementation is
         * <a href="https://github.com/FasterXML/jackson-dataformats-binary/blob/527fbb6a42358d92d493285f35a4387c482a3aaa/smile/src/main/java/com/fasterxml/jackson/dataformat/smile/SmileParserBootstrapper.java#L89-L138">here</a>
         * using the Apache 2.0 license.
         */
        @Override
        public InstrumentedSmileParser constructParser(
                int factoryFeatures,
                int generalParserFeatures,
                int smileFeatures,
                ObjectCodec codec,
                ByteQuadsCanonicalizer rootByteSymbols)
                throws IOException, JsonParseException {
            // 13-Mar-2021, tatu: [dataformats-binary#252] Create canonicalizing OR
            //    placeholder, depending on settings
            ByteQuadsCanonicalizer can = rootByteSymbols.makeChildOrPlaceholder(factoryFeatures);
            // We just need a single byte, really, to know if it starts with header
            int end = _inputEnd;
            if ((_inputPtr < end) && (_in != null)) {
                int count = _in.read(_inputBuffer, end, _inputBuffer.length - end);
                if (count > 0) {
                    _inputEnd += count;
                }
            }

            InstrumentedSmileParser smileParser = new InstrumentedSmileParser(
                    _context,
                    generalParserFeatures,
                    smileFeatures,
                    codec,
                    can,
                    _in,
                    _inputBuffer,
                    _inputPtr,
                    _inputEnd,
                    _bufferRecyclable,
                    instrumentation);
            boolean hadSig = false;

            if (_inputPtr >= _inputEnd) { // only the case for empty doc
                // 11-Oct-2012, tatu: Actually, let's allow empty documents even if
                //   header signature would otherwise be needed. This is useful for
                //   JAX-RS provider, empty PUT/POST payloads.
                return smileParser;
            }
            final byte firstByte = _inputBuffer[_inputPtr];
            if (firstByte == SmileConstants.HEADER_BYTE_1) {
                // need to ensure it gets properly handled so caller won't see the signature
                hadSig = smileParser.handleSignature(true, true);
            }

            if (!hadSig && SmileParser.Feature.REQUIRE_HEADER.enabledIn(smileFeatures)) {
                // Ok, first, let's see if it looks like plain JSON...
                String msg;

                if (firstByte == '{' || firstByte == '[') {
                    msg = "Input does not start with Smile format header (first byte = 0x"
                            + Integer.toHexString(firstByte & 0xFF) + ") -- rather, it starts with '"
                            + ((char) firstByte)
                            + "' (plain JSON input?) -- can not parse";
                } else {
                    msg = "Input does not start with Smile format header (first byte = 0x"
                            + Integer.toHexString(firstByte & 0xFF)
                            + ") and parser has REQUIRE_HEADER enabled: can not parse";
                }
                throw new JsonParseException(smileParser, msg);
            }
            return smileParser;
        }
    }

    private static final class InstrumentedSmileParser extends SmileParser {
        private final ParserInstrumentation instrumentation;

        InstrumentedSmileParser(
                IOContext ctxt,
                int parserFeatures,
                int smileFeatures,
                ObjectCodec codec,
                ByteQuadsCanonicalizer sym,
                InputStream in,
                byte[] inputBuffer,
                int start,
                int end,
                boolean bufferRecyclable,
                ParserInstrumentation instrumentation) {
            super(ctxt, parserFeatures, smileFeatures, codec, sym, in, inputBuffer, start, end, bufferRecyclable);
            this.instrumentation = instrumentation;
        }

        @Override
        public String nextTextValue() throws IOException {
            return instrumentation.recordStringLength(super.nextTextValue());
        }

        @Override
        public String getText() throws IOException {
            return instrumentation.recordStringLength(super.getText());
        }

        @Override
        public String getValueAsString() throws IOException {
            return instrumentation.recordStringLength(super.getValueAsString());
        }

        @Override
        public String getValueAsString(String def) throws IOException {
            return instrumentation.recordStringLength(super.getValueAsString(def));
        }

        @Override
        public boolean handleSignature(boolean consumeFirstByte, boolean throwException) throws IOException {
            // overridden to expose access to the boostrapper
            return super.handleSignature(consumeFirstByte, throwException);
        }
    }
}
