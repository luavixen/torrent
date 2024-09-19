package dev.foxgirl.torrent.bencode2

/** Represents a Bencode list. */
class BencodeList : ArrayList<Bencode>, MutableList<Bencode>, Bencode {

    constructor() : super()
    constructor(capacity: Int) : super(capacity)
    constructor(collection: Collection<Bencode>) : super(collection)

    override fun add(element: Bencode) = super.add(element)
    fun add(element: Int) = add(Bencode.of(element))
    fun add(element: Long) = add(Bencode.of(element))
    fun add(element: String) = add(Bencode.of(element))

    override fun add(index: Int, element: Bencode) = super.add(index, element)
    fun add(index: Int, element: Int) = add(index, Bencode.of(element))
    fun add(index: Int, element: Long) = add(index, Bencode.of(element))
    fun add(index: Int, element: String) = add(index, Bencode.of(element))

    override operator fun set(index: Int, element: Bencode) = super.set(index, element)
    operator fun set(index: Int, element: Int) = set(index, Bencode.of(element))
    operator fun set(index: Int, element: Long) = set(index, Bencode.of(element))
    operator fun set(index: Int, element: String) = set(index, Bencode.of(element))

    override fun copy(): BencodeList {
        val list = BencodeList(size)
        for (element in this) {
            list.add(element.copy())
        }
        return list
    }

}