package dev.foxgirl.torrent.metainfo2

/** Thrown when converting Bencode into a subclass of [Meta] fails. */
class InvalidMetaException : Exception {

    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)

}