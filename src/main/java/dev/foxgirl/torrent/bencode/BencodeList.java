package dev.foxgirl.torrent.bencode;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BencodeList extends ArrayList<BencodeElement> implements BencodeElement {

    public BencodeList() {
        super();
    }

    public BencodeList(int capacity) {
        super(capacity);
    }

    public BencodeList(@NotNull List<? extends BencodeElement> source) {
        super(Objects.requireNonNull(source, "Argument 'source'"));
    }

    public void addInteger(long value) {
        add(BencodeInteger.of(value));
    }
    public void addInteger(int index, long value) {
        add(index, BencodeInteger.of(value));
    }

    public void addString(@NotNull String value) {
        add(BencodeString.of(value));
    }
    public void addString(int index, @NotNull String value) {
        add(index, BencodeString.of(value));
    }

    public void setInteger(int index, long value) {
        set(index, BencodeInteger.of(value));
    }

    public void setString(int index, @NotNull String value) {
        set(index, BencodeString.of(value));
    }

    @Override
    public @NotNull BencodeType getType() {
        return BencodeType.LIST;
    }

    @Override
    public @NotNull BencodeList copy() {
        var list = new BencodeList(size());
        for (var element : this) {
            list.add(element.copy());
        }
        return list;
    }

    @Override
    public @NotNull BencodeList asList() {
        return this;
    }

    @Override
    public @NotNull String toString() {
        return super.toString();
    }
}
