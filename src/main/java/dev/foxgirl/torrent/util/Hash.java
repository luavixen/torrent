package dev.foxgirl.torrent.util;

import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA1Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

public final class Hash {

    public enum Algorithm {
        SHA1 {
            public @NotNull SHA1Digest createDigest() {
                return new SHA1Digest();
            }
        },
        SHA256 {
            public @NotNull SHA256Digest createDigest() {
                return new SHA256Digest();
            }
        };

        public abstract @NotNull Digest createDigest();
    }

    public static final class DigestOutputStream extends OutputStream {
        private final Digest digest;
        private Hash hash;
        private long count;

        public DigestOutputStream(@NotNull Algorithm algorithm) {
            Objects.requireNonNull(algorithm, "Argument 'algorithm'");
            digest = algorithm.createDigest();
        }

        public synchronized long getCount() {
            return count;
        }

        @Override
        public synchronized void write(int value) {
            if (hash != null) {
                throw new IllegalStateException("Stream is closed");
            }
            digest.update((byte) value);
            count++;
        }

        @Override
        public synchronized void write(byte @NotNull [] bytes, int offset, int length) {
            Objects.requireNonNull(bytes, "Argument 'bytes'");
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (hash != null) {
                throw new IllegalStateException("Stream is closed");
            }
            digest.update(bytes, offset, length);
            count += length;
        }

        @Override
        public synchronized void close() {
            if (hash == null) {
                hash = new Hash(digest);
            }
        }

        public synchronized @NotNull Hash complete() {
            close();
            return hash;
        }
    }

    public static @NotNull DigestOutputStream digestOutputStream(@NotNull Algorithm algorithm) {
        return new DigestOutputStream(algorithm);
    }

    public static @NotNull Hash digest(@NotNull Algorithm algorithm, @NotNull InputStream inputStream) throws IOException {
        Objects.requireNonNull(algorithm, "Argument 'algorithm'");
        Objects.requireNonNull(inputStream, "Argument inputStream'");
        var digest = algorithm.createDigest();
        var buffer = new byte[8192];
        while (true) {
            int count = inputStream.read(buffer, 0, buffer.length);
            if (count > 0) digest.update(buffer, 0, count);
            if (count < 0) break;
        }
        return new Hash(digest);
    }

    public static @NotNull Hash digest(@NotNull Algorithm algorithm, byte @NotNull [] bytes, int offset, int length) {
        Objects.requireNonNull(algorithm, "Argument 'algorithm'");
        Objects.requireNonNull(bytes, "Argument 'bytes'");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        Digest digest = algorithm.createDigest();
        digest.update(bytes, offset, length);
        return new Hash(digest);
    }

    public static @NotNull Hash digest(@NotNull Algorithm algorithm, byte @NotNull [] bytes) {
        return digest(algorithm, bytes, 0, bytes.length);
    }

    public static @NotNull Hash of(byte @NotNull [] bytes, int offset, int length) {
        Objects.requireNonNull(bytes, "Argument 'bytes'");
        Objects.checkFromIndexSize(offset, length, bytes.length);
        return new Hash(Arrays.copyOfRange(bytes, offset, offset + length));
    }

    public static @NotNull Hash of(byte @NotNull [] bytes) {
        return of(bytes, 0, bytes.length);
    }

    private final byte[] bytes;

    private Hash(byte[] bytes) {
        this.bytes = bytes;
    }

    private Hash(Digest digest) {
        bytes = new byte[digest.getDigestSize()];
        digest.doFinal(bytes, 0);
    }

    public int length() {
        return bytes.length;
    }

    public byte @NotNull [] getBytes() {
        return bytes.clone();
    }

    @Override
    public @NotNull String toString() {
        return Hex.toHexString(bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        var that = (Hash) obj;
        return Arrays.equals(bytes, that.bytes);
    }

}
