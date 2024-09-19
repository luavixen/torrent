package dev.foxgirl.torrent.metainfo2

import dev.foxgirl.torrent.bencode2.Bencode
import dev.foxgirl.torrent.bencode2.BencodeMap

sealed class Meta(extraFields: BencodeMap?) {

    val extraFields: BencodeMap = extraFields?.copy() ?: BencodeMap()
        get() = field.copy()

    abstract fun toBencode(): Bencode

}