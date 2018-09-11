/*
 * Copyright 2018 Palantir Technologies, Inc. All rights reserved.
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

package com.palantir.remoting3.okhttp;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import javax.annotation.Nullable;
import okio.Buffer;
import okio.BufferedSource;
import okio.ByteString;
import okio.Options;
import okio.Sink;
import okio.Timeout;

@SuppressWarnings("DesignForExtension")
class ForwardingBufferedSource implements BufferedSource {
    private final BufferedSource delegate;

    ForwardingBufferedSource(BufferedSource delegate) {
        this.delegate = delegate;
    }

    @Override
    public Buffer buffer() {
        return delegate.buffer();
    }

    @Override
    public boolean exhausted() throws IOException {
        return delegate.exhausted();
    }

    @Override
    public void require(long byteCount) throws IOException {
        delegate.require(byteCount);
    }

    @Override
    public boolean request(long byteCount) throws IOException {
        return delegate.request(byteCount);
    }

    @Override
    public byte readByte() throws IOException {
        return delegate.readByte();
    }

    @Override
    public short readShort() throws IOException {
        return delegate.readShort();
    }

    @Override
    public short readShortLe() throws IOException {
        return delegate.readShortLe();
    }

    @Override
    public int readInt() throws IOException {
        return delegate.readInt();
    }

    @Override
    public int readIntLe() throws IOException {
        return delegate.readIntLe();
    }

    @Override
    public long readLong() throws IOException {
        return delegate.readLong();
    }

    @Override
    public long readLongLe() throws IOException {
        return delegate.readLongLe();
    }

    @Override
    public long readDecimalLong() throws IOException {
        return delegate.readDecimalLong();
    }

    @Override
    public long readHexadecimalUnsignedLong() throws IOException {
        return delegate.readHexadecimalUnsignedLong();
    }

    @Override
    public void skip(long byteCount) throws IOException {
        delegate.skip(byteCount);
    }

    @Override
    public ByteString readByteString() throws IOException {
        return delegate.readByteString();
    }

    @Override
    public ByteString readByteString(long byteCount) throws IOException {
        return delegate.readByteString(byteCount);
    }

    @Override
    public int select(Options options) throws IOException {
        return delegate.select(options);
    }

    @Override
    public byte[] readByteArray() throws IOException {
        return delegate.readByteArray();
    }

    @Override
    public byte[] readByteArray(long byteCount) throws IOException {
        return delegate.readByteArray(byteCount);
    }

    @Override
    public int read(byte[] sink) throws IOException {
        return delegate.read(sink);
    }

    @Override
    public void readFully(byte[] sink) throws IOException {
        delegate.readFully(sink);
    }

    @Override
    public int read(byte[] sink, int offset, int byteCount) throws IOException {
        return delegate.read(sink, offset, byteCount);
    }

    @Override
    public void readFully(Buffer sink, long byteCount) throws IOException {
        delegate.readFully(sink, byteCount);
    }

    @Override
    public long readAll(Sink sink) throws IOException {
        return delegate.readAll(sink);
    }

    @Override
    public String readUtf8() throws IOException {
        return delegate.readUtf8();
    }

    @Override
    public String readUtf8(long byteCount) throws IOException {
        return delegate.readUtf8(byteCount);
    }

    @Override
    @Nullable
    public String readUtf8Line() throws IOException {
        return delegate.readUtf8Line();
    }

    @Override
    public String readUtf8LineStrict() throws IOException {
        return delegate.readUtf8LineStrict();
    }

    @Override
    public String readUtf8LineStrict(long limit) throws IOException {
        return delegate.readUtf8LineStrict(limit);
    }

    @Override
    public int readUtf8CodePoint() throws IOException {
        return delegate.readUtf8CodePoint();
    }

    @Override
    public String readString(Charset charset) throws IOException {
        return delegate.readString(charset);
    }

    @Override
    public String readString(long byteCount, Charset charset) throws IOException {
        return delegate.readString(byteCount, charset);
    }

    @Override
    public long indexOf(byte b) throws IOException {
        return delegate.indexOf(b);
    }

    @Override
    public long indexOf(byte b, long fromIndex) throws IOException {
        return delegate.indexOf(b, fromIndex);
    }

    @Override
    public long indexOf(byte b, long fromIndex, long toIndex) throws IOException {
        return delegate.indexOf(b, fromIndex, toIndex);
    }

    @Override
    public long indexOf(ByteString bytes) throws IOException {
        return delegate.indexOf(bytes);
    }

    @Override
    public long indexOf(ByteString bytes, long fromIndex) throws IOException {
        return delegate.indexOf(bytes, fromIndex);
    }

    @Override
    public long indexOfElement(ByteString targetBytes) throws IOException {
        return delegate.indexOfElement(targetBytes);
    }

    @Override
    public long indexOfElement(ByteString targetBytes, long fromIndex) throws IOException {
        return delegate.indexOfElement(targetBytes, fromIndex);
    }

    @Override
    public boolean rangeEquals(long offset, ByteString bytes) throws IOException {
        return delegate.rangeEquals(offset, bytes);
    }

    @Override
    public boolean rangeEquals(long offset, ByteString bytes, int bytesOffset, int byteCount) throws IOException {
        return delegate.rangeEquals(offset, bytes, bytesOffset, byteCount);
    }

    @Override
    public InputStream inputStream() {
        return delegate.inputStream();
    }

    @Override
    public long read(Buffer sink, long byteCount) throws IOException {
        return delegate.read(sink, byteCount);
    }

    @Override
    public Timeout timeout() {
        return delegate.timeout();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return delegate.read(dst);
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }
}
