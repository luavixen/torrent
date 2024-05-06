package dev.foxgirl.torrent

import dev.foxgirl.torrent.bencode.*
import dev.foxgirl.torrent.client.Client
import dev.foxgirl.torrent.client.Identity
import dev.foxgirl.torrent.client.Peer
import dev.foxgirl.torrent.client.Swarm
import dev.foxgirl.torrent.metainfo.MetaInfo
import dev.foxgirl.torrent.util.DefaultExecutors
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.*
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousSocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.concurrent.ThreadLocalRandom

fun main() {

    System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE")
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_DATE_TIME_KEY, "false")
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_THREAD_NAME_KEY, "false")
    System.setProperty(org.slf4j.impl.SimpleLogger.SHOW_LOG_NAME_KEY, "false")

    val metainfo = MetaInfo.fromBencode(BencodeDecoder.decodeFromStream(Files.newInputStream(Path.of("./torrents/silly.torrent")).buffered()))

    val client = Client(Identity.generateDefault(InetSocketAddress(InetAddress.getLocalHost(), 8008)))
    val swarm = client.createSwarm(metainfo.info)

    val peerAddress = InetSocketAddress(InetAddress.getByName("127.0.0.1"), 51413)
    val peerChannel = AsynchronousSocketChannel.open()
    val peer = Peer(client, peerChannel)

    peerChannel.connect(peerAddress)
    peer.establishOutgoing(peerAddress, metainfo.infoHash).get()

    Thread.sleep(4000)

    println("peer: $peer")
    println("lastIncomingMessageTime: ${peer.protocol.lastIncomingMessageTime}")
    println("lastOutgoingMessageTime: ${peer.protocol.lastOutgoingMessageTime}")

    /*
    Thread.sleep(2000)

    peer.setChoking(false).get()
    peer.setInterested(true).get()
    */

    Thread.sleep(20 * 1000)

    peer.close()
    DefaultExecutors.shutdown()

}

/*
fun main() {

    val metainfo = MetaInfo.fromBencode(BencodeDecoder.decodeFromStream(Files.newInputStream(Path.of("./torrents/silly.torrent")).buffered()))

    println("downloading torrent: $metainfo")

    val clientIdentity = Identity.generateDefault(InetSocketAddress(InetAddress.getLocalHost(), 8008))
    val peerAddress = InetSocketAddress(InetAddress.getLocalHost(), 51413)

    val channel = AsynchronousSocketChannel.open()
    val peer = Protocol(channel)

    peer.connectEvent.subscribe { _, identity ->
        println("peer connected: $identity")
    }
    peer.receiveEvent.subscribe { _, message ->
        println("peer received: $message")
    }
    peer.closeEvent.subscribe { _, cause ->
        println("peer closed: ${cause.stackTraceToString()}")
    }

    println("connecting to peer: $peerAddress")
    channel.connect(peerAddress)

    println("performing handshake")
    peer.establishOutgoing(metainfo.infoHash, clientIdentity, peerAddress).get()

    peer.send(MessageImpl(MessageType.UNCHOKE)).get()
    println("sent unchoke")
    peer.send(MessageImpl(MessageType.INTERESTED)).get()
    println("sent interested")

    val unchokeFuture = CompletableFuture<Unit>().also {
        peer.receiveEvent.subscribe { subscription, message ->
            if (message.type == MessageType.UNCHOKE) {
                subscription.unsubscribe()
                it.complete(Unit)
            }
        }
    }
    unchokeFuture.get()
    println("received unchoke")

    println("starting download")
    val fileWriterExecutor = Executors.newSingleThreadExecutor()
    val file = RandomAccessFile(Path.of("./silly.mp4").toFile(), "rw")

    file.setLength(metainfo.info.totalLength)

    val inflightSemaphoreSize = 10
    val inflightSemaphore = Semaphore(inflightSemaphoreSize)
    val inflightRequestFutures = mutableListOf<CompletableFuture<Void>>()

    peer.receiveEvent.subscribe { _, message ->
        if (message.type == MessageType.PIECE) {
            inflightSemaphore.release()
            val index = message.payload.getInt().toLong()
            val offset = message.payload.getInt().toLong()
            val buffer = ByteArray(message.payload.remaining()); message.payload.get(buffer)
            println("received piece ${Objects.hash(index.toInt(), offset.toInt(), buffer.size)}: index: $index, offset: $offset, length: ${buffer.size}")
            fileWriterExecutor.execute {
                synchronized(file) {
                    val position = index * metainfo.info.pieceLength + offset
                    println("writing piece ${Objects.hash(index.toInt(), offset.toInt(), buffer.size)} to file: ${buffer.size} bytes to position $position")
                    file.seek(position)
                    file.write(buffer)
                }
            }
        }
    }

    for ((index, piece) in metainfo.info.pieces.withIndex()) {
        var offset = 0
        val pieceLength =
            if (index == metainfo.info.pieces.lastIndex) {
                (metainfo.info.totalLength - (index * metainfo.info.pieceLength)).toInt()
            } else {
                (metainfo.info.pieceLength).toInt()
            }

        while (offset < pieceLength) {
            val requestLength = minOf(16 * 1024, pieceLength - offset)
            println("requesting piece ${Objects.hash(index.toInt(), offset.toInt(), requestLength.toInt())}: index: $index, offset: $offset, length: $requestLength")
            inflightSemaphore.acquire()
            inflightRequestFutures.add(peer.send(MessageImpl(MessageType.REQUEST, ByteBuffer.allocate(12).putInt(index).putInt(offset).putInt(requestLength).flip())))
            offset += requestLength
        }
    }

    CompletableFuture.allOf(*inflightRequestFutures.toTypedArray()).get()
    Thread.sleep(3000)

    println("download complete")
    fileWriterExecutor.shutdown()
    file.close()

    println("closing connection")
    peer.close()
    channel.close()

    println("done!")

}
*/

fun main1() {

    val files = listOf(
        Path.of("./torrents/big-buck-bunny.torrent"),
        Path.of("./torrents/sintel.torrent"),
        Path.of("./torrents/debian.torrent"),
        Path.of("./torrents/ubuntu.torrent"),
        Path.of("./torrents/archlinux.torrent"),
        Path.of("./torrents/photoshop.torrent"),
    )

    for (file in files) {
        val stream = Files.newInputStream(file).buffered()
        val torrent = BencodeDecoder.decodeFromStream(stream).asMap()
        stream.close()
        val metainfo = MetaInfo.fromBencode(torrent)
        println(metainfo)
    }

    val identity = Identity.generateDefault(InetSocketAddress(0))

}

fun main2() {

    val torrent = BencodeDecoder.decodeFromStream(Files.newInputStream(Path.of("./big-buck-bunny.torrent")).buffered()).asMap()
    val torrentInfo = torrent["info"]!!.asMap()
    val torrentInfoHash = MessageDigest.getInstance("SHA-1").digest(BencodeEncoder.encodeToBytes(torrent["info"]!!))
    val torrentLength =
        (torrentInfo["piece length"]!!.asInteger().value) *
        (torrentInfo["pieces"]!!.asString().length() / 20).toLong()

    println("name: ${torrentInfo["name"]}")
    println("announce: ${torrent["announce"]}")
    println("infohash: ${torrentInfoHash.joinToString("") { "%02x".format(it) }}")
    println("length: $torrentLength")

    val announceURI = URI.create(torrent["announce-list"]!!.asList()[2]!!.asList()[0]!!.toString())
    val announceAddress = InetAddress.getByName(announceURI.host)
    val announcePort = announceURI.port

    // -LU-1000-<12 random digits>
    val peerID = "-LU1000-${(0 until 12).map { ('0'..'9').random() }.joinToString("")}".toByteArray()

    require(peerID.size == 20)

    println("peerID: ${peerID.decodeToString()}")

    val socket = DatagramSocket()
    val buffer = ByteBuffer.allocate(65536)

    val packetConnectC2STransaction = ThreadLocalRandom.current().nextInt()

    buffer.clear()
    buffer.putLong(0x41727101980) // connection_id
    buffer.putInt(0) // action
    buffer.putInt(packetConnectC2STransaction) // transaction_id

    val packetConnectC2S = DatagramPacket(buffer.array(), buffer.position(), announceAddress, announcePort)
    socket.send(packetConnectC2S)
    buffer.clear()

    val packetConnectS2C = DatagramPacket(buffer.array(), buffer.array().size)
    socket.receive(packetConnectS2C)

    val packetConnectS2CAction = buffer.getInt() // action
    val packetConnectS2CTransaction = buffer.getInt() // transaction_id
    val packetConnectS2CID = buffer.getLong() // connection_id

    require(packetConnectS2CAction == 0)
    require(packetConnectS2CTransaction == packetConnectC2STransaction)
    require(packetConnectS2CID != 0L)

    val packetAnnounceC2STransaction = ThreadLocalRandom.current().nextInt()

    buffer.clear()
    buffer.putLong(packetConnectS2CID) // connection_id
    buffer.putInt(1) // action
    buffer.putInt(packetAnnounceC2STransaction) // transaction_id
    buffer.put(torrentInfoHash) // info_hash
    buffer.put(peerID) // peer_id
    buffer.putLong(0) // downloaded
    buffer.putLong(torrentLength) // left
    buffer.putLong(0) // uploaded
    buffer.putInt(0) // event
    buffer.putInt(0) // IP address
    buffer.putInt(ThreadLocalRandom.current().nextInt()) // key
    buffer.putInt(50) // num_want
    buffer.putShort(6881.toShort()) // port

    val packetAnnounceC2S = DatagramPacket(buffer.array(), buffer.position(), announceAddress, announcePort)
    socket.send(packetAnnounceC2S)
    buffer.clear()

    val packetAnnounceS2C = DatagramPacket(buffer.array(), buffer.array().size)
    socket.receive(packetAnnounceS2C)

    val packetAnnounceS2CAction = buffer.getInt() // action
    val packetAnnounceS2CTransaction = buffer.getInt() // transaction_id
    val packetAnnounceS2CInterval = buffer.getInt() // interval
    val packetAnnounceS2CLeachers = buffer.getInt() // leachers
    val packetAnnounceS2CSeeders = buffer.getInt() // seeders

    val packetAnnounceS2CPeerCount = (packetAnnounceS2C.length - buffer.position()) / 6

    val peers = mutableListOf<Pair<InetAddress, Int>>()

    // peers.add(InetAddress.getByName("127.0.0.1") to 51413)

    for (i in 0 until packetAnnounceS2CPeerCount) {
        val peerAddress = InetAddress.getByAddress(ByteArray(4).also(buffer::get))
        val peerPort = buffer.getShort().toInt() and 0xFFFF
        peers.add(peerAddress to peerPort)
    }

    require(packetAnnounceS2CAction == 1)
    require(packetAnnounceS2CTransaction == packetAnnounceC2STransaction)

    println("peers:")
    for ((peerAddress, peerPort) in peers) {
        println("  - ${peerAddress.hostAddress}:${peerPort}")
    }

    socket.close()
    buffer.clear()

    var conn = Socket()

    for (peer in peers) {
        val peerSocket = Socket()
        val peerSocketAddr = InetSocketAddress(peer.first, peer.second)
        try {
            println("connecting to peer: $peerSocketAddr")
            peerSocket.connect(peerSocketAddr, 3000)
            peerSocket.soTimeout = 8000
            conn = peerSocket
            break
        } catch (cause: Exception) {
            println("failed to connect to peer: $cause")
        }
    }

    if (!conn.isConnected) {
        println("failed to connect to any peers")
        return
    }

    val connIn = DataInputStream(conn.getInputStream())
    val connOut = DataOutputStream(conn.getOutputStream())

    buffer.put(19) // pstrlen
    buffer.put("BitTorrent protocol".toByteArray()) // pstr
    buffer.put(ByteArray(8)) // reserved
    buffer.put(torrentInfoHash) // info_hash
    buffer.put(peerID) // peer_id

    connOut.write(buffer.array(), 0, buffer.position())
    buffer.clear()

    val handshakePstrlen = connIn.readByte() // pstrlen
    val handshakePstr = connIn.readNBytes(handshakePstrlen.toInt()).decodeToString() // pstr
    val handshakeReserved = connIn.readNBytes(8) // reserved
    val handshakeInfoHash = connIn.readNBytes(20) // info_hash
    val handshakePeerID = connIn.readNBytes(20) // peer_id

    require(handshakePstr == "BitTorrent protocol")
    require(handshakeInfoHash contentEquals torrentInfoHash)

    println("handshake complete with peer: ${handshakePeerID.decodeToString()}")



    conn.close()

    println("Bye~")

    /*
    println("Hello World!")

    val torrentBytesOriginal = Files.readAllBytes(Path.of("./debian.torrent"))

    val torrent = BencodeDecoder.decodeFromBytes(torrentBytesOriginal).asMap()
    val torrentInfo = torrent["info"]!!.asMap()

    val torrentBytesEncoded = BencodeEncoder.encodeToBytes(torrent)

    println("Does my encoder not suck? " + (torrentBytesOriginal contentEquals torrentBytesEncoded))

    val infohash = MessageDigest.getInstance("SHA-1").digest(BencodeEncoder.encodeToBytes(torrent["info"]!!))
    val infohashEncoded = URLEncoder.encode(String(infohash, Charsets.ISO_8859_1), Charsets.ISO_8859_1).lowercase()

    println("infohash: ${infohash.joinToString("") { "%02x".format(it) }}")
    println("infohash encoded: $infohashEncoded")

    var url = URI.create(torrent.asMap()["announce"]!!.toString()).toString()
    url += "?info_hash=$infohashEncoded"
    url += "&peer_id=%ff%fe%fd%fc%fb%fa%f9%f8%f7%f6%f5%f4%f3%f2%f1%f0%ef%ee%ed%ec"
    url += "&port=6881"
    url += "&uploaded=0"
    url += "&downloaded=0"
    url += "&left=" + torrent["info"]!!.asMap()["length"]!!.toString()

    println(url)

    val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
    val response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofInputStream())

    println(response)
    println(response.body().readNBytes(30).decodeToString())
    */

}

operator fun BencodeMap.get(key: String): BencodeElement? = get(BencodeString.of(key))
operator fun BencodeMap.contains(key: String): Boolean = contains(BencodeString.of(key))
