package dev.foxgirl.torrent.bencode2

sealed interface Bencode {

    fun isInteger() = false
    fun isString() = false
    fun isList() = false
    fun isMap() = false

    fun asInteger(): BencodeInteger = throw ClassCastException()
    fun asString(): BencodeString = throw ClassCastException()
    fun asList(): BencodeList = throw ClassCastException()
    fun asMap(): BencodeMap = throw ClassCastException()

}
