package dev.foxgirl.torrent.util;

import org.jetbrains.annotations.NotNull;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.CompletionHandler;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class IO {

    private IO() {
    }

    public static int readN(@NotNull InputStream stream, byte @NotNull [] buffer, int offset, int length) throws IOException {
        Objects.requireNonNull(stream, "Argument 'stream'");
        Objects.requireNonNull(buffer, "Argument 'buffer'");
        Objects.checkFromIndexSize(offset, length, buffer.length);
        int read = 0;
        while (read < length) {
            int count = stream.read(buffer, offset + read, length - read);
            if (count < 0) break;
            read += count;
        }
        if (read > length) {
            throw new IOException("Read too many bytes, expected count " + length + ", actual count " + read);
        }
        return read;
    }

    public static int readAll(@NotNull InputStream stream, byte @NotNull [] buffer, int offset, int length) throws IOException {
        int read = readN(stream, buffer, offset, length);
        if (read < length) {
            throw new EOFException("Unexpected EOF, expected count " + length + ", actual count " + read);
        }
        return read;
    }

    private static final class AsynchronousByteChannelOperation implements CompletionHandler<Integer, Void> {
        private final AsynchronousByteChannel channel;
        private final ByteBuffer buffer;
        private final boolean writing;
        private final boolean errorOnEOF;

        private final CompletableFuture<Integer> future = new CompletableFuture<>();

        private int count = 0;

        private AsynchronousByteChannelOperation(
                @NotNull AsynchronousByteChannel channel,
                @NotNull ByteBuffer buffer,
                boolean writing,
                boolean errorOnEOF
        ) {
            Objects.requireNonNull(channel, "Argument 'channel'");
            Objects.requireNonNull(buffer, "Argument 'buffer'");
            this.channel = channel;
            this.buffer = buffer;
            this.writing = writing;
            this.errorOnEOF = errorOnEOF;
        }

        private CompletableFuture<Integer> operate() {
            if (writing) {
                channel.write(buffer, null, this);
            } else {
                channel.read(buffer, null, this);
            }
            return future;
        }

        private void complete() {
            if (!writing) {
                buffer.flip();
            }
            future.complete(count);
        }
        private void completeExceptionally(Throwable cause) {
            future.completeExceptionally(cause);
        }

        @Override
        public void completed(Integer result, Void attachment) {
            if (result < 0) {
                if (errorOnEOF) {
                    if (count == 0) {
                        completeExceptionally(new EOFException("Unexpected EOF while " + (writing ? "writing" : "reading")));
                    } else {
                        completeExceptionally(new EOFException("Unexpected EOF after " + (writing ? "writing " : "reading ") + count + " bytes"));
                    }
                } else {
                    complete();
                }
            } else {
                count += result;
                if (buffer.hasRemaining()) {
                    operate();
                } else {
                    complete();
                }
            }
        }

        @Override
        public void failed(Throwable cause, Void attachment) {
            completeExceptionally(cause);
        }
    }

    public static @NotNull CompletableFuture<@NotNull Integer> asyncChannelRead(@NotNull AsynchronousByteChannel channel, @NotNull ByteBuffer buffer) {
        return new AsynchronousByteChannelOperation(channel, buffer, false, false).operate();
    }
    public static @NotNull CompletableFuture<@NotNull Integer> asyncChannelReadAll(@NotNull AsynchronousByteChannel channel, @NotNull ByteBuffer buffer) {
        return new AsynchronousByteChannelOperation(channel, buffer, false, true).operate();
    }

    public static @NotNull CompletableFuture<Integer> asyncChannelWrite(@NotNull AsynchronousByteChannel channel, @NotNull ByteBuffer buffer) {
        return new AsynchronousByteChannelOperation(channel, buffer, true, false).operate();
    }
    public static @NotNull CompletableFuture<Integer> asyncChannelWriteAll(@NotNull AsynchronousByteChannel channel, @NotNull ByteBuffer buffer) {
        return new AsynchronousByteChannelOperation(channel, buffer, true, true).operate();
    }

    public static byte @NotNull [] readArray(@NotNull InputStream stream, int length) throws IOException {
        byte[] buffer = new byte[length];
        readAll(stream, buffer, 0, length);
        return buffer;
    }

    public static byte @NotNull [] getArray(@NotNull ByteBuffer buffer, int length) {
        Objects.requireNonNull(buffer, "Argument 'buffer'");
        if (length < 0) {
            throw new IllegalArgumentException("Length is negative");
        }
        if (length > buffer.remaining()) {
            throw new IllegalArgumentException("Length is greater than remaining bytes, length " + length + ", remaining " + buffer.remaining());
        }
        byte[] array = new byte[length]; buffer.get(array);
        return array;
    }

    public static byte @NotNull [] getArray(@NotNull ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "Argument 'buffer'");
        return getArray(buffer, buffer.remaining());
    }

    public static @NotNull InputStream getInputStream(@NotNull ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "Argument 'buffer'");
        return new InputStream() {
            @Override
            public int available() {
                return buffer.remaining();
            }
            @Override
            public int read() {
                return buffer.hasRemaining() ? buffer.get() & 0xFF : -1;
            }
            @Override
            public int read(byte @NotNull [] bytes, int offset, int length) {
                int count = Math.min(length, buffer.remaining());
                buffer.get(bytes, offset, count);
                return count;
            }
            @Override
            public byte[] readAllBytes() {
                return getArray(buffer);
            }
            @Override
            public byte[] readNBytes(int length) {
                return getArray(buffer, Math.min(length, buffer.remaining()));
            }
            @Override
            public int readNBytes(byte[] b, int off, int len) {
                int n = read(b, off, len);
                return n == -1 ? 0 : n;
            }
        };
    }

    public static @NotNull OutputStream getOutputStream(@NotNull ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "Argument 'buffer'");
        return new OutputStream() {
            @Override
            public void write(int b) {
                buffer.put((byte) b);
            }
            @Override
            public void write(byte @NotNull [] bytes, int offset, int length) {
                buffer.put(bytes, offset, length);
            }
        };
    }

}
