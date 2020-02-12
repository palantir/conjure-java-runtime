/*
 * (c) Copyright 2019 Palantir Technologies Inc. All rights reserved.
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

import com.palantir.logsafe.exceptions.SafeRuntimeException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPrivateKeySpec;

/**
 * Minimal DER parser that can handle ASN.1 (PKCS#1) private key's based on <a
 * href="https://tools.ietf.org/html/rfc8017">RFC 8017, appendix C</a>.
 *
 * <p>Handles private key in following format ignoring otherPrimeinfos, i.e. validates that version is two-prime(0).
 *
 * <pre>
 * RSAPrivateKey ::= SEQUENCE {
 *     version           Version,
 *     modulus           INTEGER,  -- n
 *     publicExponent    INTEGER,  -- e
 *     privateExponent   INTEGER,  -- d
 *     prime1            INTEGER,  -- p
 *     prime2            INTEGER,  -- q
 *     exponent1         INTEGER,  -- d mod (p-1)
 *     exponent2         INTEGER,  -- d mod (q-1)
 *     coefficient       INTEGER,  -- (inverse of q) mod p
 *     otherPrimeInfos   OtherPrimeInfos OPTIONAL
 * }
 *
 * Version ::= INTEGER { two-prime(0), multi(1) }
 *     (CONSTRAINED BY
 *       {-- version MUST
 *  be multi if otherPrimeInfos present --})
 *
 * OtherPrimeInfos ::= SEQUENCE SIZE(1..MAX) OF OtherPrimeInfo
 *
 * OtherPrimeInfo ::= SEQUENCE {
 *     prime             INTEGER,  -- ri
 *     exponent          INTEGER,  -- di
 *     coefficient       INTEGER   -- ti
 * }
 * </pre>
 *
 * <p>see <a href="https://en.wikipedia.org/wiki/X.690">DER</a> format specification for explanation behind length
 * parsing and tag values. ASN.1 uses CONSTRUCTED form of SEQUENCE tag hence the value is 48 instead of 16.
 */
public final class Pkcs1PrivateKeyReader {
    /** Tag value indicating an ASN.1 "INTEGER" value. */
    private static final byte INTEGER = 0x02;

    /** Tag value indicating an ASN.1 "SEQUENCE" (zero to N elements, order is significant). */
    private static final byte SEQUENCE = 0x30;

    private final ByteBuffer derBytes;

    public Pkcs1PrivateKeyReader(byte[] derBytes) {
        this.derBytes = ByteBuffer.wrap(derBytes);
    }

    public RSAPrivateKeySpec readRsaKey() {
        byte tag = derBytes.get();
        if (tag != SEQUENCE) {
            throw new SafeRuntimeException("Expected SEQUENCE byte (0x30) at the beginning of RSA private key");
        }
        int length = readLength();
        // cap the read length
        derBytes.limit(derBytes.position() + length);
        BigInteger version = readNumber();
        if (version.intValue() == 1) {
            throw new SafeRuntimeException("Only version 0 (two-prime) RSA keys are supported");
        }

        BigInteger modulus = readNumber();
        BigInteger publicExponent = readNumber();
        BigInteger privateExponent = readNumber();
        BigInteger primeP = readNumber();
        BigInteger primeQ = readNumber();
        BigInteger primeExponentP = readNumber();
        BigInteger primeExponentQ = readNumber();
        BigInteger crtCoefficient = readNumber();
        if (publicExponent.signum() == 0
                || primeExponentP.signum() == 0
                || primeExponentQ.signum() == 0
                || primeP.signum() == 0
                || primeQ.signum() == 0
                || crtCoefficient.signum() == 0) {
            return new RSAPrivateKeySpec(modulus, privateExponent);
        } else {
            return new RSAPrivateCrtKeySpec(
                    modulus,
                    publicExponent,
                    privateExponent,
                    primeP,
                    primeQ,
                    primeExponentP,
                    primeExponentQ,
                    crtCoefficient);
        }
    }

    private BigInteger readNumber() {
        byte tag = derBytes.get();
        if (tag != INTEGER) {
            throw new SafeRuntimeException("Expected INTEGER byte (0x02) when reading a number");
        }
        int length = readLength();
        byte[] numberBytes = new byte[length];
        System.arraycopy(derBytes.array(), derBytes.position(), numberBytes, 0, length);

        // move position in the buffer since arraycopy doesn't do it
        derBytes.position(derBytes.position() + length);
        return new BigInteger(numberBytes);
    }

    private int readLength() {
        byte lengthByte = derBytes.get();
        if (lengthByte == -1) {
            throw new SafeRuntimeException("Unable to decode length from der bytes");
        }

        if ((lengthByte & 0x080) == 0x00) { // short form, 1 byte length
            return lengthByte;
        } else if ((lengthByte & 0x07f) == 0x00) { // indefinite length encoding
            throw new SafeRuntimeException("Indefinite length encoding is not supported");
        } else { // long form encoding
            int lengthOctets = lengthByte & 0x7f;
            // cap at 4 bytes for length value, 4GB of data.
            if (lengthOctets > 4) {
                throw new SafeRuntimeException("Values bigger than 4GBs are not supported");
            }
            int length = 0;
            for (int i = 0; i < lengthOctets; i++) {
                length <<= 8;
                length += derBytes.get() & 0x0ff;
            }

            return length;
        }
    }
}
