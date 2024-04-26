package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;

public final class BitField {

    private final Object lock = new Object();

    private final @NotNull Info info;
    private final @NotNull BitSet bitset;

    public BitField(@NotNull Info info) {
        this.info = info;
        this.bitset = new BitSet(info.getPieceCount());
    }

    public @NotNull Info getInfo() {
        return info;
    }

    public @NotNull BitSet getBitSet() {
        synchronized (lock) {
            return (BitSet) bitset.clone();
        }
    }

    public byte @NotNull [] toArray() {
        synchronized (lock) {
            var bytes = new byte[(info.getPieceCount() + 7) / 8];
            for (int i = 0, length = bytes.length; i < length; i++) {
                for (int j = 0; j < 8; j++) {
                    if (bitset.get(i * 8 + j)) {
                        bytes[i] |= (byte) (1 << j);
                    }
                }
            }
            return bytes;
        }
    }

    public boolean get(int index) {
        synchronized (lock) {
            return bitset.get(index);
        }
    }
    public void set(int index, boolean value) {
        synchronized (lock) {
            bitset.set(index, value);
        }
    }
    public void set(int index) {
        set(index, true);
    }
    public void clear(int index) {
        set(index, false);
    }

    public void setAll() {
        synchronized (lock) {
            bitset.set(0, info.getPieceCount());
        }
    }
    public void clearAll() {
        synchronized (lock) {
            bitset.clear();
        }
    }

    public void and(@NotNull BitSet other) {
        synchronized (lock) {
            bitset.and(other);
        }
    }
    public void and(@NotNull BitField other) {
        synchronized (other.lock) {
            and(other.bitset);
        }
    }
    public void or(@NotNull BitSet other) {
        synchronized (lock) {
            bitset.or(other);
        }
    }
    public void or(@NotNull BitField other) {
        synchronized (other.lock) {
            or(other.bitset);
        }
    }

    public boolean isComplete() {
        synchronized (lock) {
            return bitset.cardinality() == info.getPieceCount();
        }
    }
    public boolean isEmpty() {
        synchronized (lock) {
            return bitset.isEmpty();
        }
    }

    public float getPercentage() {
        synchronized (lock) {
            return (float) bitset.cardinality() * 100.0F / (float) info.getPieceCount();
        }
    }
    public int getPercentageInteger() {
        synchronized (lock) {
            return Math.round(getPercentage());
        }
    }

    @Override
    public @NotNull String toString() {
        return "BitField{infoHash=" + info.getHash() + ", percentage=" + getPercentageInteger() + "%}";
    }

}
