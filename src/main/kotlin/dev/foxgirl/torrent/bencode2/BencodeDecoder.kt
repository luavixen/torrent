package dev.foxgirl.torrent.bencode2

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/** Decodes Bencode from an [InputStream]. */
class BencodeDecoder(private val input: InputStream) {

    private fun read(): Char {
        val value = input.read()
        if (value < 0) {
            throw EOFException("Unexpected end of Bencode stream")
        }
        return value.toChar()
    }
    private fun read(length: Int): ByteArray {
        val bytes = ByteArray(length)
        var total = 0
        while (total < length) {
            val count = input.read(bytes)
            if (count < 0) {
                throw EOFException("Unexpected end of Bencode stream")
            }
            total += count
        }
        return bytes
    }

    /** Decodes the next Bencode element. */
    @Throws(IOException::class)
    fun decode(): Bencode {
        try {
            return decodeNext(read());
        } catch (cause: StackOverflowError) {
            throw BencodeDecodeException("Stack overflow", cause)
        }
    }

    private fun decodeNext(char: Char): Bencode {
        if (char in '0'..'9') {
            return decodeNextString(char)
        }
        if (char == 'i') {
            return decodeNextInteger()
        }
        if (char == 'l') {
            return decodeNextList()
        }
        if (char == 'd') {
            return decodeNextMap()
        }
        throw BencodeDecodeException("Invalid start of element '$char'")
    }

    private fun decodeNextString(char: Char): BencodeString {
        var char = char
        var length = 0

        while (true) {
            length = length * 10 + (char - '0')
            if (length < 0) {
                throw BencodeDecodeException("String length overflow")
            }

            char = read()
            if (char == ':') {
                return BencodeString(read(length))
            }
            if (char !in '0'..'9') {
                throw BencodeDecodeException("Invalid string length")
            }
        }
    }

    private fun decodeNextInteger(): BencodeInteger {
        var char = read()

        val negative = char == '-'
        if (negative) {
            char = read()
        }

        var value = 0L
        while (char != 'e') {
            if (char < '0' || char > '9') {
                throw BencodeDecodeException("Invalid integer")
            }
            value = value * 10 + (char - '0')
            if (value < 0) {
                throw BencodeDecodeException("Integer overflow")
            }
            char = read()
        }

        return BencodeInteger(if (negative) -value else value)
    }

    private fun decodeNextList(): BencodeList {
        val list = BencodeList()
        while (true) {
            val char = read()
            if (char == 'e') {
                return list
            } else {
                list.add(decodeNext(char))
            }
        }
    }

    private fun decodeNextMap(): BencodeMap {
        val map = BencodeMap()

        while (true) {
            val char = read()
            if (char == 'e') {
                return map
            }

            val key = decodeNext(char)
            if (!key.isString()) {
                throw BencodeDecodeException("Invalid map key, expected string")
            }

            val value = decodeNext(read())
            if (map.putIfAbsent(key.asString(), value) != null) {
                throw BencodeDecodeException("Duplicate map key")
            }
        }
    }

}