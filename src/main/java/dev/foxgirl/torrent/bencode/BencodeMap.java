package dev.foxgirl.torrent.bencode;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public final class BencodeMap extends TreeMap<@NotNull BencodeString, @NotNull BencodeElement> implements BencodeElement {

    public BencodeMap() {
        super();
    }

    public BencodeMap(@NotNull Map<? extends BencodeString, ? extends BencodeElement> source) {
        super(Objects.requireNonNull(source, "Argument 'source'"));
    }

    public boolean containsKey(@NotNull BencodeString key) {
        return super.containsKey(key);
    }
    public boolean containsKey(@NotNull String key) {
        return containsKey(BencodeString.of(key));
    }

    public @Nullable BencodeElement get(@NotNull BencodeString key) {
        return super.get(key);
    }
    public @Nullable BencodeElement get(@NotNull String key) {
        return get(BencodeString.of(key));
    }

    public @NotNull BencodeElement getOrDefault(@NotNull BencodeString key, @NotNull BencodeElement defaultValue) {
        return super.getOrDefault(key, defaultValue);
    }
    public @NotNull BencodeElement getOrDefault(@NotNull String key, @NotNull BencodeElement defaultValue) {
        return getOrDefault(BencodeString.of(key), defaultValue);
    }

    @Override
    public @Nullable BencodeElement put(@NotNull BencodeString key, @NotNull BencodeElement value) {
        return super.put(key, value);
    }
    public @Nullable BencodeElement put(@NotNull String key, @NotNull BencodeElement value) {
        return put(BencodeString.of(key), value);
    }

    @Override
    public @Nullable BencodeElement putIfAbsent(@NotNull BencodeString key, @NotNull BencodeElement value) {
        return super.putIfAbsent(key, value);
    }
    public @Nullable BencodeElement putIfAbsent(@NotNull String key, @NotNull BencodeElement value) {
        return putIfAbsent(BencodeString.of(key), value);
    }

    public void putInteger(@NotNull BencodeString key, long value) {
        put(key, BencodeInteger.of(value));
    }
    public void putInteger(@NotNull String key, long value) {
        put(key, BencodeInteger.of(value));
    }

    public void putString(@NotNull BencodeString key, @NotNull String value) {
        put(key, BencodeString.of(value));
    }
    public void putString(@NotNull String key, @NotNull String value) {
        put(key, BencodeString.of(value));
    }

    public @Nullable BencodeElement remove(@NotNull BencodeString key) {
        return super.remove(key);
    }
    public @Nullable BencodeElement remove(@NotNull String key) {
        return remove(BencodeString.of(key));
    }

    public boolean remove(@NotNull BencodeString key, Object value) {
        return super.remove(key, value);
    }
    public boolean remove(@NotNull String key, Object value) {
        return remove(BencodeString.of(key), value);
    }

    @Override
    public @Nullable BencodeElement replace(@NotNull BencodeString key, @NotNull BencodeElement value) {
        return super.replace(key, value);
    }
    public @Nullable BencodeElement replace(@NotNull String key, @NotNull BencodeElement value) {
        return replace(BencodeString.of(key), value);
    }

    @Override
    public boolean replace(@NotNull BencodeString key, @NotNull BencodeElement oldValue, @NotNull BencodeElement newValue) {
        return super.replace(key, oldValue, newValue);
    }
    public boolean replace(@NotNull String key, @NotNull BencodeElement oldValue, @NotNull BencodeElement newValue) {
        return replace(BencodeString.of(key), oldValue, newValue);
    }

    @Override
    public @NotNull BencodeType getType() {
        return BencodeType.MAP;
    }

    @Override
    public @NotNull BencodeMap copy() {
        var map = new BencodeMap();
        for (var entry : entrySet()) {
            map.put(entry.getKey().copy(), entry.getValue().copy());
        }
        return map;
    }

    @Override
    public @NotNull BencodeMap asMap() {
        return this;
    }

    @Override
    public @NotNull String toString() {
        return super.toString();
    }

}
