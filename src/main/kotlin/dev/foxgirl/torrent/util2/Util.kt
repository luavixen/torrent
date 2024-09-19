package dev.foxgirl.torrent.util2

import java.nio.ByteBuffer

fun ByteBuffer.toByteArray(): ByteArray {
    return ByteArray(remaining()).also(::get)
}
