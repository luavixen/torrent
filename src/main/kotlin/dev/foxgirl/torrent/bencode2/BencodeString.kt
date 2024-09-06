package dev.foxgirl.torrent.bencode2

import java.util.*

class BencodeString : Bencode, Comparable<BencodeString> {

    private val array: ByteArray

    constructor(bytes: ByteArray) {
        array = bytes.copyOf()
    }
    constructor(bytes: ByteArray, fromIndex: Int, toIndex: Int) {
        array = bytes.copyOfRange(fromIndex, toIndex)
    }
    constructor(string: String) {
        array = string.toByteArray()
    }

    val value get() = array.decodeToString()
    val bytes get() = array.copyOf()

    override fun isString() = true
    override fun asString() = this

    override fun hashCode() = array.contentHashCode()
    override fun toString() = value

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is BencodeString) return false
        return array.contentEquals(other.array)
    }

    override fun compareTo(other: BencodeString): Int {
        return Arrays.compare(array, other.array)
    }

}