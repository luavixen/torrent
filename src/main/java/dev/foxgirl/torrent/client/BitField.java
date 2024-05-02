package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public final class BitField {

    private final @NotNull Info info;
    private final @NotNull BitSet bitset;

    public BitField(@NotNull Info info) {
        this.info = info;
        this.bitset = new BitSet(info.getPieceCount());
    }

    public @NotNull Info getInfo() {
        return info;
    }

    public synchronized @NotNull BitSet getBitSet() {
        return (BitSet) bitset.clone();
    }

    public int byteLength() {
        return (info.getPieceCount() + 7) / 8;
    }

    public synchronized byte @NotNull [] toArray() {
        var bytes = new byte[byteLength()];
        for (int i = 0, length = bytes.length; i < length; i++) {
            for (int j = 0; j < 8; j++) {
                if (bitset.get(i * 8 + j)) {
                    bytes[i] |= (byte) (1 << j);
                }
            }
        }
        return bytes;
    }

    public synchronized boolean get(int index) {
        return bitset.get(index);
    }
    public synchronized void set(int index, boolean value) {
        bitset.set(index, value);
    }
    public void set(int index) {
        set(index, true);
    }
    public void clear(int index) {
        set(index, false);
    }

    public synchronized void setAll() {
        bitset.set(0, info.getPieceCount());
    }
    public synchronized void clearAll() {
        bitset.clear();
    }

    public synchronized void and(@NotNull BitSet other) {
        bitset.and(other);
    }
    public synchronized void or(@NotNull BitSet other) {
        bitset.or(other);
    }

    public void and(@NotNull BitField other) {
        synchronized (other) {
            and(other.bitset);
        }
    }
    public void or(@NotNull BitField other) {
        synchronized (other) {
            or(other.bitset);
        }
    }

    public synchronized boolean isComplete() {
        return bitset.cardinality() == info.getPieceCount();
    }
    public synchronized boolean isEmpty() {
        return bitset.isEmpty();
    }

    public synchronized float getPercentage() {
        return (float) bitset.cardinality() * 100.0F / (float) info.getPieceCount();
    }
    public int getPercentageInteger() {
        return Math.round(getPercentage());
    }

    @Override
    public @NotNull String toString() {
        return "BitField{infoHash=" + info.getInfoHash() + ", percentage=" + getPercentageInteger() + "%}";
    }

}
