package dev.foxgirl.torrent.metainfo;

import dev.foxgirl.torrent.bencode.BencodeElement;
import dev.foxgirl.torrent.bencode.BencodeList;
import dev.foxgirl.torrent.bencode.BencodeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

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

    private static final class Path extends AbstractList<String> {
        private final String[] elements;
        private final int hash;

        private Path(List<String> path) {
            this.elements = new String[path.size()]; path.toArray(elements);
            this.hash = Arrays.hashCode(elements);
            for (var element : elements) {
                if (element == null) {
                    throw new IllegalArgumentException("Path element is null");
                }
            }
        }

        @Override
        public String get(int index) {
            return elements[index];
        }

        @Override
        public int size() {
            return elements.length;
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null) return false;
            if (obj instanceof Path that) {
                return hash == that.hash && Arrays.equals(elements, that.elements);
            }
            return super.equals(obj);
        }
    }

    private final Path path;
    private final long length;

    public FileInfo(@NotNull List<@NotNull String> path, long length, @Nullable BencodeMap extraFields) {
        super(extraFields);

        Objects.requireNonNull(path, "Argument 'path'");

        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path is empty");
        }

        if (length < 0) {
            throw new IllegalArgumentException("Length is negative");
        }

        this.path = new Path(path);
        this.length = length;
    }

    public @NotNull List<@NotNull String> getPath() {
        return path;
    }

    public @NotNull String getPathString() {
        return String.join("/", path);
    }

    public long getLength() {
        return length;
    }

    @Override
    public @NotNull String toString() {
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
