package dev.foxgirl.torrent.metainfo;

import dev.foxgirl.torrent.bencode.BencodeElement;
import dev.foxgirl.torrent.bencode.BencodeMap;
import dev.foxgirl.torrent.bencode.BencodePrimitive;
import dev.foxgirl.torrent.bencode.BencodeString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class InfoContainer {

    private static final @NotNull BencodeMap EMPTY_EXTRA_FIELDS = new BencodeMap();

    private final @NotNull BencodeMap extraFields;

    protected InfoContainer(@Nullable BencodeMap extraFields) {
        if (extraFields == null || extraFields.isEmpty()) {
            this.extraFields = EMPTY_EXTRA_FIELDS;
        } else {
            this.extraFields = extraFields.copy();
        }
    }

    public final @NotNull BencodeMap getExtraFields() {
        return extraFields.copy();
    }

    private static @Nullable BencodeElement copyIfMutable(@Nullable BencodeElement element) {
        if (element == null) return null;
        return element instanceof BencodePrimitive ? element : element.copy();
    }

    public final @Nullable BencodeElement getExtraField(@NotNull BencodeString key) {
        return copyIfMutable(extraFields.get(key));
    }
    public final @Nullable BencodeElement getExtraField(@NotNull String key) {
        return copyIfMutable(extraFields.get(key));
    }

    public final boolean containsExtraField(@NotNull BencodeString key) {
        return extraFields.containsKey(key);
    }
    public final boolean containsExtraField(@NotNull String key) {
        return extraFields.containsKey(key);
    }

    public final @NotNull Set<@NotNull BencodeString> getExtraFieldKeys() {
        return extraFields.keySet();
    }

    public abstract @NotNull BencodeMap toBencode();

}
