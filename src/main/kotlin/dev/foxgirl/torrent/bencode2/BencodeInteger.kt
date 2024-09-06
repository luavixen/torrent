package dev.foxgirl.torrent.bencode2

class BencodeInteger(val value: Long) : Bencode, Comparable<BencodeInteger> {

    override fun isInteger() = true
    override fun asInteger() = this

    override fun hashCode() = value.hashCode()
    override fun toString() = value.toString()

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is BencodeInteger) return false
        return value == other.value
    }

    override fun compareTo(other: BencodeInteger) = value.compareTo(other.value)

}