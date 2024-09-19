package dev.foxgirl.torrent.bencode2

/** Represents a Bencode element. */
sealed interface Bencode {

    /** Creates a deep copy of this Bencode element. */
    fun copy(): Bencode

    companion object {

        fun of(value: Int) = BencodeInteger(value)
        fun of(value: Long) = BencodeInteger(value)

        fun of(value: String) = BencodeString(value)
        fun of(value: ByteArray) = BencodeString(value)

        fun <T> collect(source: Collection<T>, transform: (T) -> Bencode): BencodeList {
            return source.mapTo(BencodeList(source.size), transform)
        }

    }

}
