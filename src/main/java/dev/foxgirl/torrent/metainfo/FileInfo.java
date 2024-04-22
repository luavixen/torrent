package dev.foxgirl.torrent.metainfo;

import dev.foxgirl.torrent.bencode.BencodeElement;
import dev.foxgirl.torrent.bencode.BencodeList;
import dev.foxgirl.torrent.bencode.BencodeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

public final class FileInfo extends InfoContainer {

    public static @NotNull FileInfo fromBencode(BencodeElement source) throws InvalidBencodeException {
        if (source == null) {
            throw new InvalidBencodeException("File is null");
        }
        if (!source.getType().isMap()) {
            throw new InvalidBencodeException("File is not a map");
        }

        var map = new BencodeMap(source.asMap());

        var pathValue = map.remove("path");
        if (pathValue == null) {
            throw new InvalidBencodeException("File path is missing");
        }
        if (!pathValue.getType().isList()) {
            throw new InvalidBencodeException("File path is not a list");
        }

        var path = new ArrayList<String>(pathValue.asList().size());
        for (var pathValueElement : pathValue.asList()) {
            if (pathValueElement == null) {
                throw new InvalidBencodeException("File path element is null");
            }
            if (!pathValueElement.getType().isString()) {
                throw new InvalidBencodeException("File path element is not a string");
            }
            path.add(pathValueElement.asString().getValue());
        }

        var lengthValue = map.remove("length");
        if (lengthValue == null) {
            throw new InvalidBencodeException("File length is missing");
        }
        if (!lengthValue.getType().isInteger()) {
            throw new InvalidBencodeException("File length is not an integer");
        }

        var length = lengthValue.asInteger().getValue();

        return new FileInfo(path, length, map);
    }

    private final @NotNull List<@NotNull String> path;
    private final long length;

    public FileInfo(@NotNull List<@NotNull String> path, long length, @Nullable BencodeMap extraFields) {
        super(extraFields);

        Objects.requireNonNull(path, "Argument 'path'");

        path = List.copyOf(path);
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path is empty");
        }

        if (length < 0) {
            throw new IllegalArgumentException("Length is negative");
        }

        this.path = path;
        this.length = length;
    }

    public @NotNull List<@NotNull String> getPath() {
        return path;
    }

    public long getLength() {
        return length;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", "InfoFile{", "}")
                .add("path=" + path)
                .add("length=" + length)
                .toString();
    }

    @Override
    public @NotNull BencodeMap toBencode() {
        var map = getExtraFields();

        var pathValue = new BencodeList(path.size());
        path.forEach(pathValue::addString);
        map.put("path", pathValue);

        map.putInteger("length", length);

        return map;
    }

}
