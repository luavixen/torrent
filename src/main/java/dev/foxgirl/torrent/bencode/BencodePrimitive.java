package dev.foxgirl.torrent.bencode;

public sealed interface BencodePrimitive extends BencodeElement permits BencodeInteger, BencodeString {
}
