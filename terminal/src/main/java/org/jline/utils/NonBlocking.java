/*
 * Copyright (c) 2002-2018, the original author or authors.
 *
 * This software is distributable under the BSD license. See the terms of the
 * BSD license in the documentation provided with this software.
 *
 * http://www.opensource.org/licenses/bsd-license.php
 */
package org.jline.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.UnmappableCharacterException;

public class NonBlocking {

    public static NonBlockingPumpReader nonBlockingPumpReader() {
        return new NonBlockingPumpReader();
    }

    public static NonBlockingPumpReader nonBlockingPumpReader(int size) {
        return new NonBlockingPumpReader(size);
    }

    public static NonBlockingPumpInputStream nonBlockingPumpInputStream() {
        return new NonBlockingPumpInputStream();
    }

    public static NonBlockingPumpInputStream nonBlockingPumpInputStream(int size) {
        return new NonBlockingPumpInputStream(size);
    }

    public static NonBlockingInputStream nonBlockingStream(NonBlockingReader reader, Charset encoding) {
        return new NonBlockingReaderInputStream(reader, encoding);
    }

    public static NonBlockingInputStream nonBlocking(String name, InputStream inputStream) {
        if (inputStream instanceof NonBlockingInputStream) {
            return (NonBlockingInputStream) inputStream;
        }
        return new NonBlockingInputStreamImpl(name, inputStream);
    }

    public static NonBlockingReader nonBlocking(String name, Reader reader) {
        if (reader instanceof NonBlockingReader) {
            return (NonBlockingReader) reader;
        }
        return new NonBlockingReaderImpl(name, reader);
    }

    public static NonBlockingReader nonBlocking(String name, InputStream inputStream, Charset encoding) {
        return new NonBlockingInputStreamReader(nonBlocking(name, inputStream), encoding);
    }

    private static class NonBlockingReaderInputStream extends NonBlockingInputStream {

        private final NonBlockingReader reader;
        private final CharsetEncoder encoder;

        // To encode a character with multiple bytes (e.g. certain Unicode characters)
        // we need enough space to encode them. Reading would fail if the read() method
        // is used to read a single byte in these cases.
        // Use this buffer to ensure we always have enough space to encode a character.
        private final ByteBuffer bytes;
        private final CharBuffer chars;

        private NonBlockingReaderInputStream(NonBlockingReader reader, Charset charset) {
            this(reader, charset, 4);
        }

        private NonBlockingReaderInputStream(NonBlockingReader reader, Charset charset, int bufferSize) {
            this.reader = reader;
            this.encoder = charset.newEncoder()
                    .onUnmappableCharacter(CodingErrorAction.REPLACE)
                    .onMalformedInput(CodingErrorAction.REPLACE);
            this.bytes = ByteBuffer.allocate((int) Math.ceil(bufferSize * encoder.maxBytesPerChar()));
            this.chars = CharBuffer.allocate(bufferSize);
            // No input available after initialization
            this.bytes.limit(0);
        }

        @Override
        public int available() throws IOException {
            return (int) (reader.available() * this.encoder.averageBytesPerChar())
                    + bytes.remaining();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }

        @Override
        public int read(long timeout, boolean isPeek) throws IOException {
            boolean isInfinite = (timeout <= 0L);
            while (!bytes.hasRemaining() && (isInfinite || timeout > 0L)) {
                long start = 0;
                if (!isInfinite) {
                    start = System.currentTimeMillis();
                }
                int c = reader.read(timeout);
                if (c == EOF) {
                    return EOF;
                }
                if (c >= 0) {
                    int l = chars.limit();
                    chars.array()[chars.arrayOffset() + l] = (char) c;
                    chars.limit(l + 1);
                    int p = bytes.position();
                    l = bytes.limit();
                    bytes.position(l);
                    bytes.limit(bytes.capacity());
                    CoderResult result = encoder.encode(chars, bytes, false);
                    l = bytes.position();
                    bytes.position(p);
                    bytes.limit(l);
                    if (result.isUnderflow()) {
                        if (chars.limit() == chars.capacity()) {
                            chars.compact();
                            chars.limit(chars.position());
                            chars.position(0);
                        }
                    } else if (result.isOverflow()) {
                        if (bytes.limit() == bytes.capacity()) {
                            bytes.compact();
                            bytes.limit(bytes.position());
                            bytes.position(0);
                        }
                    } else if (result.isMalformed()) {
                        throw new MalformedInputException(result.length());
                    } else if (result.isUnmappable()) {
                        throw new UnmappableCharacterException(result.length());
                    }
                }
                if (!isInfinite) {
                    timeout -= System.currentTimeMillis() - start;
                }
            }
            if (bytes.hasRemaining()) {
                if (isPeek) {
                    return bytes.get(bytes.position());
                } else {
                    return bytes.get();
                }
            } else {
                return READ_EXPIRED;
            }
        }

    }

    private static class NonBlockingInputStreamReader extends NonBlockingReader {

        private final NonBlockingInputStream nbis;
        private final CharsetDecoder decoder;
        private final ByteBuffer bytes;
        private final CharBuffer chars;

        public NonBlockingInputStreamReader(NonBlockingInputStream inputStream, Charset encoding) {
            this(inputStream,
                (encoding != null ? encoding : Charset.defaultCharset()).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPLACE)
                    .onUnmappableCharacter(CodingErrorAction.REPLACE),
                4);
        }

        public NonBlockingInputStreamReader(NonBlockingInputStream nbis, CharsetDecoder decoder, int bufferSize) {
            this.nbis = nbis;
            this.decoder = decoder;
            this.bytes = ByteBuffer.allocate((int) Math.ceil(bufferSize / decoder.maxCharsPerByte()));
            this.chars = CharBuffer.allocate(bufferSize);
            this.bytes.limit(0);
            this.chars.limit(0);
        }

        @Override
        protected int read(long timeout, boolean isPeek) throws IOException {
            boolean isInfinite = (timeout <= 0L);
            while (!chars.hasRemaining() && (isInfinite || timeout > 0L)) {
                long start = 0;
                if (!isInfinite) {
                    start = System.currentTimeMillis();
                }
                int b = nbis.read(timeout);
                if (b == EOF) {
                    return EOF;
                }
                if (b >= 0) {
                    if (bytes.capacity() - bytes.limit() < 1) {
                        bytes.compact();
                        bytes.limit(bytes.position());
                        bytes.position(0);
                    }
                    if (chars.capacity() - chars.limit() < 2) {
                        chars.compact();
                        chars.limit(chars.position());
                        chars.position(0);
                    }
                    int l = bytes.limit();
                    bytes.array()[bytes.arrayOffset() + l] = (byte) b;
                    bytes.limit(l + 1);
                    int p = chars.position();
                    l = chars.limit();
                    chars.position(l);
                    chars.limit(chars.capacity());
                    decoder.decode(bytes, chars, false);
                    l = chars.position();
                    chars.position(p);
                    chars.limit(l);
                }

                if (!isInfinite) {
                    timeout -= System.currentTimeMillis() - start;
                }
            }
            if (chars.hasRemaining()) {
                if (isPeek) {
                    return chars.get(chars.position());
                } else {
                    return chars.get();
                }
            } else {
                return READ_EXPIRED;
            }
        }

        @Override
        public void shutdown() {
            nbis.shutdown();
        }

        @Override
        public void close() throws IOException {
            nbis.close();
        }
    }
}
