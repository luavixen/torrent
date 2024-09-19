package dev.foxgirl.torrent.bencode2

import java.util.TreeMap

/** Represents a Bencode map/dictionary. */
class BencodeMap : TreeMap<BencodeString, Bencode>, MutableMap<BencodeString, Bencode>, Bencode {

    constructor() : super()
    constructor(map: Map<BencodeString, Bencode>) : super(map)

    override operator fun get(key: BencodeString) = super.get(key)
    operator fun get(key: String) = get(Bencode.of(key))

    override fun remove(key: BencodeString) = super<TreeMap>.remove(key)
    fun remove(key: String) = remove(Bencode.of(key))

    override fun put(key: BencodeString, value: Bencode) = super.put(key, value)
    fun put(key: String, value: Bencode) = put(Bencode.of(key), value)
    fun put(key: String, value: Int) = put(Bencode.of(key), Bencode.of(value))
    fun put(key: String, value: Long) = put(Bencode.of(key), Bencode.of(value))
    fun put(key: String, value: String) = put(Bencode.of(key), Bencode.of(value))

    operator fun set(key: BencodeString, value: Bencode) = put(key, value)
    operator fun set(key: String, value: Bencode) = put(key, value)
    operator fun set(key: String, value: Int) = put(key, value)
    operator fun set(key: String, value: Long) = put(key, value)
    operator fun set(key: String, value: String) = put(key, value)

    override fun copy(): BencodeMap {
        val map = BencodeMap()
        for ((key, value) in this) {
            map[key] = value.copy()
        }
        return map
    }

}