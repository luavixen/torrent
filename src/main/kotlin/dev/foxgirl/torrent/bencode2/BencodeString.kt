package dev.foxgirl.torrent.bencode2

import dev.foxgirl.torrent.util2.*
import java.nio.ByteBuffer
import java.util.*

/** Represents a Bencode string. */
class BencodeString : Bencode, Comparable<BencodeString> {

    private val array: ByteArray

    constructor(bytes: ByteArray) {
        array = bytes.copyOf()
    }
    constructor(bytes: ByteArray, fromIndex: Int, toIndex: Int) {
        array = bytes.copyOfRange(fromIndex, toIndex)
    }
    constructor(buffer: ByteBuffer) {
        array = buffer.toByteArray()
    }
    constructor(string: String) {
        array = string.toByteArray()
    }

    constructor(parts: Iterable<ByteArray>) {
        array = ByteArray(parts.sumOf(ByteArray::size))
        parts.fold(0) { offset, part ->
            System.arraycopy(part, 0, array, offset, part.size)
            offset + part.size
        }
    }

    val value get() = array.decodeToString()
    val bytes get() = array.clone()

    val length get() = array.size

    /** Copies as many bytes of this string as possible into [destination], starting from [offset]. */
    fun copyTo(destination: ByteArray, offset: Int): Int {
        if (offset < 0 || offset > length) {
            throw IndexOutOfBoundsException("Invalid offset $offset for length $length")
        }
        val count = Math.min(length - offset, destination.size)
        if (count > 0) {
            System.arraycopy(array, offset, destination, 0, count)
        }
        return count
    }

    override fun copy(): BencodeString = this

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