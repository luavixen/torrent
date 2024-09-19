package dev.foxgirl.torrent.util2

import org.bouncycastle.crypto.Digest
import org.bouncycastle.crypto.digests.SHA1Digest
import org.bouncycastle.crypto.digests.SHA256Digest
import java.io.OutputStream
import java.util.*

class Hash {

    enum class Algorithm {
        SHA1   { override fun createDigest(): SHA1Digest = SHA1Digest()     },
        SHA256 { override fun createDigest(): SHA256Digest = SHA256Digest() };

        abstract fun createDigest(): Digest

        fun createDigest(bytes: ByteArray) = createDigest(bytes, 0, bytes.size)
        fun createDigest(bytes: ByteArray, offset: Int, length: Int): Digest {
            Objects.checkFromIndexSize(offset, length, bytes.size)
            return createDigest().apply { update(bytes, offset, length) }
        }

        fun createOutputStream() = DigestOutputStream(this)
    }

    class DigestOutputStream(algorithm: Algorithm) : OutputStream() {
        private val digest = algorithm.createDigest()

        private var hash: Hash? = null
        private var count: Long = 0

        private fun checkNotClosed() {
            check(hash == null) { "Digest already completed, stream closed." }
        }
        private fun checkFromIndexSize(fromIndex: Int, offset: Int, length: Int) {
            Objects.checkFromIndexSize(fromIndex, offset, length)
        }

        @Synchronized
        override fun write(byte: Int) {
            checkNotClosed()
            digest.update(byte.toByte())
            count++
        }

        @Synchronized
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            checkNotClosed()
            checkFromIndexSize(offset, length, bytes.size)
            digest.update(bytes, offset, length)
            count += length
        }

        @Synchronized
        override fun close() {
            if (hash == null) {
                hash = Hash(digest)
            }
        }

        @Synchronized
        fun complete(): Hash {
            close()
            return hash!!
        }
    }

    private val array: ByteArray

    constructor(bytes: ByteArray) {
        array = bytes.copyOf()
    }
    constructor(bytes: ByteArray, fromIndex: Int, toIndex: Int) {
        array = bytes.copyOfRange(fromIndex, toIndex)
    }

    constructor(digest: Digest) {
        array = ByteArray(digest.digestSize)
        digest.doFinal(array, 0)
    }

    constructor(algorithm: Algorithm, bytes: ByteArray)
            : this(algorithm.createDigest(bytes))
    constructor(algorithm: Algorithm, bytes: ByteArray, offset: Int, length: Int)
            : this(algorithm.createDigest(bytes, offset, length))

    val length get() = array.size
    val bytes get() = array.clone()

    @OptIn(ExperimentalStdlibApi::class)
    override fun toString() = array.toHexString()
    override fun hashCode() = array.contentHashCode()

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is Hash) return false
        return array.contentEquals(other.array)
    }

}
