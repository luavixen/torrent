package dev.foxgirl.torrent.bencode;

import org.jetbrains.annotations.NotNull;

public final class BencodeInteger implements BencodePrimitive, Comparable<BencodeInteger> {

    private static final BencodeInteger[] CACHE = new BencodeInteger[256];

    static {
        for (int i = 0; i < 256; i++) {
            CACHE[i] = new BencodeInteger(i - 128);
        }
    }

    public static @NotNull BencodeInteger of(long value) {
        if (value >= -128 && value < 128) {
            return CACHE[(int) value + 128];
        }
        return new BencodeInteger(value);
    }

    private final long value;

    private BencodeInteger(long value) {
        this.value = value;
    }

    public long getValue() {
        return value;
    }

    @Override
    public @NotNull BencodeType getType() {
        return BencodeType.INTEGER;
    }

    @Override
    public @NotNull BencodeInteger copy() {
        return this;
    }

    @Override
    public @NotNull BencodeInteger asInteger() {
        return this;
    }

    @Override
    public @NotNull String toString() {
        return Long.toString(value);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != getClass()) return false;
        var that = (BencodeInteger) obj;
        return value == that.value;
    }

    @Override
    public int compareTo(@NotNull BencodeInteger o) {
        return Long.compare(value, o.value);
    }

}
