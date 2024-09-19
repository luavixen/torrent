package dev.foxgirl.torrent.bencode2

import java.io.IOException

/** Thrown when [BencodeDecoder] encounters invalid Bencode. */
class BencodeDecodeException : IOException {

    constructor() : super()
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)

}