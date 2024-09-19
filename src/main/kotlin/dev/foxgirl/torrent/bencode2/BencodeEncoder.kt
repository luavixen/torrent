package dev.foxgirl.torrent.bencode2

import java.io.IOException
import java.io.OutputStream

/** Encodes Bencode elements into an [OutputStream]. */
class BencodeEncoder(private val output: OutputStream) {

    private val buffer: ByteArray = ByteArray(2048)

    private fun write(char: Char) = output.write(char.toInt())
    private fun write(string: String) = output.write(string.encodeToByteArray())

    /** Encodes this Bencode element. */
    @Throws(IOException::class)
    fun encode(element: Bencode) {
        when {
            element.isInteger() -> encode(element.asInteger())
            element.isString() -> encode(element.asString())
            element.isList() -> encode(element.asList())
            element.isMap() -> encode(element.asMap())
        }
    }

    private fun encode(element: BencodeInteger) {
        write('i')
        write(element.toString())
        write('e')
    }

    private fun encode(element: BencodeString) {
        write(element.length.toString())
        write(':')
        var offset = 0
        while (true) {
            val count = element.copyTo(buffer, offset)
            if (count < 1) break
            output.write(buffer, 0, count)
            offset += count
        }
    }

    private fun encode(element: BencodeList) {
        write('l')
        element.forEach(::encode)
        write('e')
    }

    private fun encode(element: BencodeMap) {
        write('d')
        element.forEach { (key, value) ->
            encode(key)
            encode(value)
        }
        write('e')
    }

}