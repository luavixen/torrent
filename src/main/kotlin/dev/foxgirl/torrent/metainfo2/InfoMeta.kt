package dev.foxgirl.torrent.metainfo2

import dev.foxgirl.torrent.bencode2.*
import dev.foxgirl.torrent.util2.Hash

class InfoMeta : Meta {

    val name: String
    val pieces: List<Hash>
    val pieceLength: Long
    val totalLength: Long
    val files: List<FileMeta>
    val isSingleFile: Boolean
    val isPrivate: Boolean?

    val infoHash: Hash
    val infoLength: Long

    override fun toBencode(): Bencode {
        val map = extraFields
        map["name"] = name
        map["pieces"] = BencodeString(pieces.map(Hash::bytes))
        map["piece length"] = pieceLength
        if (isSingleFile) {
            map["length"] = totalLength
        } else {
            map["files"] = Bencode.collect(files, FileMeta::toBencode)
        }
        if (isPrivate != null) {
            map["private"] = if (isPrivate) 1 else 0
        }
        return map
    }

    companion object {

        @Throws(InvalidMetaException::class)
        fun fromBencode(source: Bencode): InfoMeta {
            try {
                val map = source.asMap().copy()
                return InfoMeta()
            } catch (cause: Exception) {
                throw InvalidMetaException("Invalid Bencode for FileMeta", cause)
            }
        }

    }

}