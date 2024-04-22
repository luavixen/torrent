package dev.foxgirl.torrent.bencode;

public enum BencodeType {

    INTEGER,
    STRING,
    LIST,
    MAP;

    public boolean isInteger() {
        return this == INTEGER;
    }
    public boolean isString() {
        return this == STRING;
    }
    public boolean isList() {
        return this == LIST;
    }
    public boolean isMap() {
        return this == MAP;
    }

    @Override
    public String toString() {
        return switch (this) {
            case INTEGER -> "integer";
            case STRING -> "string";
            case LIST -> "list";
            case MAP -> "map";
        };
    }
}
