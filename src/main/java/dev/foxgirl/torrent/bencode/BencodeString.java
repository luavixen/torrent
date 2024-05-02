package dev.foxgirl.torrent.bencode;

import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.WeakHashMap;

public final class BencodeString implements BencodePrimitive, Comparable<BencodeString> {

    private static final WeakHashMap<String, BencodeString> CACHE = new WeakHashMap<>();

    public static @NotNull BencodeString of(@NotNull String value) {
        Objects.requireNonNull(value, "Argument 'value'");
        synchronized (CACHE) {
            var string = CACHE.get(value);
            if (string == null) {
                CACHE.put(value, string = new BencodeString(value.getBytes(StandardCharsets.UTF_8)));
            }
            return string;
        }
    }

    public static @NotNull BencodeString of(byte @NotNull [] value) {
        Objects.requireNonNull(value, "Argument 'value'");
        return new BencodeString(value.clone());
    }

    public static @NotNull BencodeString of(byte @NotNull [] value, int offset, int length) {
        Objects.requireNonNull(value, "Argument 'value'");
        if (offset < 0 || offset >= value.length) {
            throw new IndexOutOfBoundsException("Invalid offset " + offset + " for source array length " + value.length);
        }
        if (length < 0 || length > value.length - offset) {
            throw new IndexOutOfBoundsException("Invalid length " + length + " for source array length " + value.length + " and offset " + offset);
        }
        return new BencodeString(Arrays.copyOfRange(value, offset, offset + length));
    }

    public static @NotNull BencodeString of(@NotNull ByteBuffer value) {
        Objects.requireNonNull(value, "Argument 'value'");
        var bytes = new byte[value.remaining()]; value.get(bytes);
        return new BencodeString(bytes);
    }

    static @NotNull BencodeString wrap(byte @NotNull [] value) {
        return new BencodeString(value);
    }

    private final byte[] bytes;
    private volatile int hash;

    private BencodeString(byte[] bytes) {
        this.bytes = bytes;
    }

    private static int hash(byte[] value) {
        int hash = 0x811C9DC5;
        for (byte b : value) {
            hash ^= b;
            hash *= 16777619;
        }
        return hash != 0 ? hash : 31;
    }

    public @NotNull String getValue() {
        return getValue(StandardCharsets.UTF_8);
    }
    public @NotNull String getValue(@NotNull Charset charset) {
        return new String(bytes, charset);
    }

    public int length() {
        return bytes.length;
    }

    public boolean isEmpty() {
        return length() == 0;
    }

    public byte byteAt(int index) {
        return bytes[index];
    }

    public byte @NotNull [] getBytes() {
        return bytes.clone();
    }

    public int copyTo(byte @NotNull [] destination, int offset) {
        Objects.requireNonNull(destination, "Argument 'destination'");
        if (offset < 0 || offset > bytes.length) {
            throw new IndexOutOfBoundsException("Invalid offset " + offset + " for string of length " + length());
        }
        int count = Math.min(bytes.length - offset, destination.length);
        if (count > 0) {
            System.arraycopy(bytes, offset, destination, 0, count);
        }
        return count;
    }

    public @NotNull ByteBuffer toBuffer() {
        return ByteBuffer.wrap(bytes).asReadOnlyBuffer();
    }

    public @NotNull InputStream toInputStream() {
        return new InputStream() {
            private int index = 0;

            @Override
            public synchronized int available() {
                return bytes.length - index;
            }

            @Override
            public synchronized long skip(long count) {
                if (count < 0) return 0;
                int skipped = (int) Math.min(count, available());
                index += skipped;
                return skipped;
            }

            @Override
            public synchronized int read() {
                return index < bytes.length ? bytes[index++] & 0xFF : -1;
            }

            @Override
            public synchronized int read(byte @NotNull [] buffer, int offset, int length) {
                Objects.checkFromIndexSize(offset, length, buffer.length);
                int count = Math.min(length, available());
                if (count > 0) {
                    System.arraycopy(bytes, index, buffer, offset, count);
                    index += count;
                    return count;
                }
                return -1;
            }
        };
    }

    @Override
    public @NotNull BencodeType getType() {
        return BencodeType.STRING;
    }

    @Override
    public @NotNull BencodeString copy() {
        return this;
    }

    @Override
    public @NotNull BencodeString asString() {
        return this;
    }

    @Override
    public @NotNull String toString() {
        return getValue();
    }

    @Override
    public int hashCode() {
        var hash = this.hash;
        if (hash == 0) {
            hash = this.hash = hash(bytes);
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        var that = (BencodeString) obj;
        if (hash != 0 && that.hash != 0 && hash != that.hash) return false;
        return Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int compareTo(@NotNull BencodeString other) {
        return Arrays.compare(bytes, other.bytes);
    }

}
