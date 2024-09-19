package dev.foxgirl.torrent.metainfo2

import dev.foxgirl.torrent.bencode2.*

class FileMeta : Meta {

    private class PathList(path: List<String>) : AbstractList<String>() {
        private val elements = path.toTypedArray()
        private val hashCode = elements.contentHashCode()

        override val size get() = elements.size
        override fun get(index: Int) = elements[index]

        override fun hashCode() = hashCode
        override fun toString() = elements.contentToString()

        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other is PathList) {
                return hashCode == other.hashCode
                    && elements.contentEquals(other.elements)
            }
            return super.equals(other)
        }
    }

    val path: List<String>
    val length: Long

    constructor(path: List<String>, length: Long, extraFields: BencodeMap? = null) : super(extraFields) {
        require(path.isNotEmpty()) { "Path is empty" }
        require(length >= 0) { "Length is negative" }
        this.path = PathList(path)
        this.length = length
    }

    override fun toBencode(): Bencode {
        val map = extraFields
        map["path"] = Bencode.collect(path, Bencode::of)
        map["length"] = length
        return map
    }

    companion object {

        @Throws(InvalidMetaException::class)
        fun fromBencode(source: Bencode): FileMeta {
            try {
                val map = source.asMap().copy()
                return FileMeta(
                    path = map.remove("path").asList().map(Bencode::stringValue),
                    length = map.remove("length").asInteger().value,
                    extraFields = map,
                )
            } catch (cause: Exception) {
                throw InvalidMetaException("Invalid Bencode for FileMeta", cause)
            }
        }

    }

}