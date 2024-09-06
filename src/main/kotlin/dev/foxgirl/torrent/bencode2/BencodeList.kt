package dev.foxgirl.torrent.bencode2

class BencodeList : Bencode, ArrayList<Bencode>() {

    override fun isList() = true
    override fun asList() = this

}