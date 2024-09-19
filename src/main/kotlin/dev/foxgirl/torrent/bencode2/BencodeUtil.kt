package dev.foxgirl.torrent.bencode2

/** Returns true if this is a [BencodeInteger]. */
fun Bencode.isInteger() = this is BencodeInteger
/** Returns true if this is a [BencodeString]. */
fun Bencode.isString() = this is BencodeString
/** Returns true if this is a [BencodeList]. */
fun Bencode.isList() = this is BencodeList
/** Returns true if this is a [BencodeMap]. */
fun Bencode.isMap() = this is BencodeMap

/** Casts this value to a [BencodeInteger]. */
fun Bencode?.asInteger() = this as BencodeInteger
/** Casts this value to a [BencodeString]. */
fun Bencode?.asString() = this as BencodeString
/** Casts this value to a [BencodeList]. */
fun Bencode?.asList() = this as BencodeList
/** Casts this value to a [BencodeMap]. */
fun Bencode?.asMap() = this as BencodeMap

val Bencode.longValue: Long get() = asInteger().value
val Bencode.stringValue: String get() = asString().value

val Bencode.intValue: Int get() = Math.toIntExact(longValue)
