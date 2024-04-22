package dev.foxgirl.torrent.client;

import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Objects;
import java.util.StringJoiner;

public final class Extensions {

    private final BitSet bitset = new BitSet(64);

    private void assertArrayLength(byte[] bits) {
        if (bits.length != 8) {
            throw new IllegalArgumentException("Length of bits array is not 8");
        }
    }

    private void fromArray(byte[] bits) {
        assertArrayLength(bits);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if ((bits[i] & (1 << j)) != 0) {
                    bitset.set(i * 8 + j);
                }
            }
        }
    }
    private void toArray(byte[] bits) {
        assertArrayLength(bits);
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                if (bitset.get(i * 8 + j)) {
                    bits[i] |= (byte) (1 << j);
                }
            }
        }
    }

    public Extensions() {
    }

    public Extensions(byte @NotNull [] bits) {
        Objects.requireNonNull(bits, "Argument 'bits'");
        fromArray(bits);
    }

    public byte @NotNull [] getBits() {
        var bits = new byte[8]; toArray(bits);
        return bits;
    }

    private int assertIndex(int index) {
        if (index < 0 || index >= 64) {
            throw new IndexOutOfBoundsException("Index out of bounds: " + index);
        }
        return index;
    }

    public boolean getBit(int index) {
        return bitset.get(assertIndex(index));
    }
    public void setBit(int index, boolean value) {
        bitset.set(assertIndex(index), value);
    }

    public boolean hasNatTraversal() {
        return getBit(60);
    }
    public void setNatTraversal(boolean value) {
        setBit(60, value);
    }

    public boolean hasFastPeers() {
        return getBit(61);
    }
    public void setFastPeers(boolean value) {
        setBit(61, value);
    }

    public boolean hasDht() {
        return getBit(63);
    }
    public void setDht(boolean value) {
        setBit(63, value);
    }

    @Override
    public String toString() {
        var joiner = new StringJoiner(", ", getClass().getSimpleName() + "[", "]");
        joiner.add("hasNatTraversal()=" + hasNatTraversal());
        joiner.add("hasFastPeers()=" + hasFastPeers());
        joiner.add("hasDht()=" + hasDht());
        for (int i = 0; i < 64; i++) {
            if (getBit(i)) {
                joiner.add("bits[" + i + "]=true");
            }
        }
        return joiner.toString();
    }

}
