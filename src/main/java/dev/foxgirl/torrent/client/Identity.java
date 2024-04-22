package dev.foxgirl.torrent.client;

import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public final class Identity {

    private static final String DEFAULT_PREFIX = "-VX1000-";
    private static final byte[] DEFAULT_PREFIX_BYTES = DEFAULT_PREFIX.getBytes(StandardCharsets.ISO_8859_1);

    public static @NotNull Identity generateDefault(@NotNull InetSocketAddress address) {
        var random = ThreadLocalRandom.current();
        var buffer = ByteBuffer.allocate(20).put(DEFAULT_PREFIX_BYTES);
        while (buffer.hasRemaining()) {
            buffer.put((byte) random.nextInt('0', '9' + 1));
        }
        return new Identity(buffer.array(), address);
    }

    private final byte[] id;
    private final String idString;

    private final InetSocketAddress address;

    public Identity(@NotNull String id, @NotNull InetSocketAddress address) {
        this(id.getBytes(StandardCharsets.ISO_8859_1), address);
    }

    public Identity(byte @NotNull [] id, @NotNull InetSocketAddress address) {
        Objects.requireNonNull(id, "Argument 'id'");
        Objects.requireNonNull(address, "Argument 'address'");
        if (id.length != 20) {
            throw new IllegalArgumentException("ID length is not 20");
        }
        this.idString = formatID(this.id = id.clone());
        this.address = address;
    }

    private String formatID(byte[] id) {
        id = id.clone();
        for (int i = 0; i < id.length; i++) {
            int b = id[i] & 0xFF;
            if (!(
                (b >= '0' && b <= '9') ||
                (b >= 'A' && b <= 'Z') ||
                (b >= 'a' && b <= 'z') ||
                (b == '-')
            )) {
                id[i] = '_';
            }
        }
        return new String(id, StandardCharsets.ISO_8859_1);
    }

    public byte @NotNull [] getID() {
        return id.clone();
    }

    public @NotNull String getIDString() {
        return idString;
    }

    public @NotNull InetSocketAddress getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return "Identity{id='" + idString + "', address=" + address + "}";
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(id) + address.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        var that = (Identity) obj;
        return Arrays.equals(id, that.id) && address.equals(that.address);
    }

}
