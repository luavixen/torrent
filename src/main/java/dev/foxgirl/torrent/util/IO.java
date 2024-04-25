package dev.foxgirl.torrent.util;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.InterruptedByTimeoutException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public final class IO {

    private IO() {
    }

    public static int readN(@NotNull InputStream stream, byte @NotNull [] buffer, int offset, int length) throws IOException {
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
        return readN(stream, buffer, offset, length);
    }

    public static byte @NotNull [] readArray(@NotNull InputStream stream, int length) throws IOException {
        byte[] buffer = new byte[length];
        readAll(stream, buffer, 0, length);
        return buffer;
    }

    public static byte @NotNull [] toArray(@NotNull ByteBuffer buffer, int length) {
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

    public static byte @NotNull [] toArray(@NotNull ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "Argument 'buffer'");
        return toArray(buffer, buffer.remaining());
    }

    public static @NotNull RuntimeException wrapException(@NotNull Exception cause) {
        Objects.requireNonNull(cause, "Argument 'cause'");
        if (cause instanceof EOFException) {
            return new RuntimeException("Unexpected EOF", cause);
        }
        if (cause instanceof FileNotFoundException || cause instanceof NoSuchFileException) {
            return new RuntimeException("File not found", cause);
        }
        if (cause instanceof FileAlreadyExistsException) {
            return new RuntimeException("File already exists", cause);
        }
        if (cause instanceof FileSystemException) {
            return new RuntimeException("File system error", cause);
        }
        if (
            cause instanceof HttpTimeoutException ||
            cause instanceof SocketTimeoutException ||
            cause instanceof InterruptedByTimeoutException ||
            cause instanceof TimeoutException
        ) {
            return new RuntimeException("Timeout", cause);
        }
        if (
            cause instanceof InterruptedException ||
            cause instanceof InterruptedIOException ||
            cause instanceof ClosedByInterruptException
        ) {
            return new RuntimeException("Interrupted unexpectedly", cause);
        }
        if (cause instanceof IOException) {
            return new RuntimeException("Unexpected IO exception", cause);
        }
        return cause instanceof RuntimeException ? (RuntimeException) cause : new RuntimeException(cause);
    }

    public static void throwException(@NotNull Exception cause) {
        throw wrapException(cause);
    }

}
