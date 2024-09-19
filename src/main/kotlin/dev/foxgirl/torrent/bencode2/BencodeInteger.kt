package dev.foxgirl.torrent.bencode2

/** Represents a Bencode integer. */
class BencodeInteger(val value: Long) : Bencode, Comparable<BencodeInteger> {

    constructor(value: Byte) : this(value.toLong())
    constructor(value: Int) : this(value.toLong())

    override fun copy(): BencodeInteger = this

    override fun hashCode() = value.hashCode()
    override fun toString() = value.toString()

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is BencodeInteger) return false
        return value == other.value
    }

    override fun compareTo(other: BencodeInteger) = value.compareTo(other.value)

}