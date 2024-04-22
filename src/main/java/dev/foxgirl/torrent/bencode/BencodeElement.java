package dev.foxgirl.torrent.bencode;

import org.jetbrains.annotations.NotNull;

public sealed interface BencodeElement permits BencodePrimitive, BencodeList, BencodeMap {

    @NotNull BencodeType getType();

    @NotNull BencodeElement copy();

    default @NotNull BencodeInteger asInteger() {
        throw new UnsupportedOperationException(getType() + " cannot be cast to BencodeInteger");
    }
    default @NotNull BencodeString asString() {
        throw new UnsupportedOperationException(getType() + " cannot be cast to BencodeString");
    }
    default @NotNull BencodeList asList() {
        throw new UnsupportedOperationException(getType() + " cannot be cast to BencodeList");
    }
    default @NotNull BencodeMap asMap() {
        throw new UnsupportedOperationException(getType() + " cannot be cast to BencodeMap");
    }

}
