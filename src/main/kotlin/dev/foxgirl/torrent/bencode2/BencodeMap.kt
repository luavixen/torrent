package dev.foxgirl.torrent.bencode2

import java.util.TreeMap

class BencodeMap : Bencode, TreeMap<BencodeString, Bencode>() {

    override fun isMap() = true
    override fun asMap() = this

}