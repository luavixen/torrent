package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.bencode.BencodeDecoder;
import dev.foxgirl.torrent.bencode.BencodeElement;
import dev.foxgirl.torrent.bencode.BencodeEncoder;
import dev.foxgirl.torrent.bencode.BencodeType;
import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import dev.foxgirl.torrent.util.IO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class Peer implements Protocol.Listener, AutoCloseable {

    private final @NotNull Swarm swarm;
    private final @NotNull Protocol protocol;

    private final Object lock = new Object();

    private boolean isReady = false;

    private List<byte[]> infoParts;

    private Info info;
    private BitSet peerBitfield;
    private BitSet clientBitfield;

    private boolean isPeerChoking = true;
    private boolean isPeerInterested = false;
    private boolean isClientChoking = true;
    private boolean isClientInterested = false;

    public Peer(@NotNull Swarm swarm, @NotNull AsynchronousByteChannel channel) {
        Objects.requireNonNull(swarm, "Argument 'swarm'");
        Objects.requireNonNull(channel, "Argument 'channel'");
        this.swarm = swarm;
        this.protocol = new Protocol(channel, this);
    }

    public @NotNull CompletableFuture<@NotNull Identity> establishOutgoing(
            @NotNull Hash infoHash,
            @NotNull Identity clientIdentity,
            @NotNull InetSocketAddress peerAddress
    ) {
        return protocol.establishOutgoing(infoHash, clientIdentity, peerAddress);
    }
    public @NotNull CompletableFuture<@NotNull Identity> establishIncoming(
            @NotNull Identity clientIdentity,
            @NotNull InetSocketAddress peerAddress
    ) {
        return protocol.establishIncoming(clientIdentity, peerAddress);
    }

    public @NotNull Swarm getSwarm() {
        return swarm;
    }
    public @NotNull Protocol getProtocol() {
        return protocol;
    }

    public @NotNull Identity getClientIdentity() {
        return swarm.getIdentity();
    }
    public @NotNull Identity getPeerIdentity() {
        var identity = protocol.getIdentity();
        if (identity == null) {
            throw new IllegalStateException("Peer is not connected");
        }
        return identity;
    }

    public @NotNull Extensions getClientExtensions() {
        return swarm.getExtensions();
    }
    public @NotNull Extensions getPeerExtensions() {
        var extensions = protocol.getExtensions();
        if (extensions == null) {
            throw new IllegalStateException("Peer is not connected");
        }
        return extensions;
    }

    @Override
    public void close() {
        protocol.close();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Protocol.class);

    private void setup(Info info) {
        synchronized (lock) {
            this.info = info;
            peerBitfield = new BitSet(info.getPieceCount());
            clientBitfield = new BitSet(info.getPieceCount());
        }
        LOGGER.info("Peer {} is ready with infohash {}", getPeerIdentity(), info.getHash());
    }

    @Override
    public synchronized void onReceive(@NotNull MessageImpl message) throws Exception {
        switch (message.getType()) {
            case CHOKE -> {
                synchronized (lock) { isPeerChoking = true; }
                LOGGER.debug("Peer {} choked us", getPeerIdentity());
            }
            case UNCHOKE -> {
                synchronized (lock) { isPeerChoking = false; }
                LOGGER.debug("Peer {} unchoked us", getPeerIdentity());
            }
            case INTERESTED -> {
                synchronized (lock) { isPeerInterested = true; }
                LOGGER.debug("Peer {} is interested in us", getPeerIdentity());
            }
            case NOT_INTERESTED -> {
                synchronized (lock) { isPeerInterested = false; }
                LOGGER.debug("Peer {} is not interested in us", getPeerIdentity());
            }
            case HAVE -> {
                int pieceIndex = message.getPayload().getInt();
                if (pieceIndex < 0 || pieceIndex >= info.getPieceCount()) {
                    throw new IllegalStateException("Invalid piece index, expected [0, " + info.getPieceCount() + "), actual " + pieceIndex);
                }
                synchronized (lock) {
                    peerBitfield.set(pieceIndex);
                }
                LOGGER.debug("Peer {} has piece {}, {}%", getPeerIdentity(), pieceIndex, peerBitfield.cardinality() * 100 / info.getPieceCount());
            }
            case BITFIELD -> {
                int expectedByteCount = (info.getPieceCount() + 7) / 8;
                int actualByteCount = message.getPayload().remaining();
                if (actualByteCount != expectedByteCount) {
                    throw new IllegalStateException("Invalid bitfield length, expected " + expectedByteCount + ", actual " + actualByteCount);
                }
                peerBitfield.or(BitSet.valueOf(message.getPayload()));
                LOGGER.debug("Peer {} updated the bitfield, {}%", getPeerIdentity(), peerBitfield.cardinality() * 100 / info.getPieceCount());
            }
            case HAVE_ALL -> {
                synchronized (lock) {
                    peerBitfield.set(0, info.getPieceCount());
                }
                LOGGER.debug("Peer {} has all pieces", getPeerIdentity());
            }
            case HAVE_NONE -> {
                synchronized (lock) {
                    peerBitfield.clear();
                }
                LOGGER.debug("Peer {} has no pieces", getPeerIdentity());
            }
            case EXTENDED -> {
                var messageID = message.getPayload().get();
                if (messageID == 0) {
                    var handshakeStream = IO.getInputStream(message.getPayload());
                    var handshake = BencodeDecoder.decodeFromStream(handshakeStream);
                    getPeerExtensions().fromHandshake(handshake);
                    LOGGER.debug("Peer {} sent an extended handshake: {}", getPeerIdentity(), handshake);
                } else {
                    LOGGER.warn("Peer {} sent an unknown extended message {}", getPeerIdentity(), messageID);
                }
            }
        }
    }

    @Override
    public void onConnect(@NotNull Identity identity) {
        swarm.addPeer(this);

        var info = swarm.getInfo(protocol.getInfoHash());
        if (info == null) {
            // TODO
            throw new IllegalStateException("Peers with torrents that are not in the swarm are not supported yet");
        } else {
            setup(info);
        }

        if (getPeerExtensions().hasExtensionProtocol()) {
            var clientExtensions = getClientExtensions();

            var clientTcpPort = identity.getSocketAddress().getPort();
            if (clientTcpPort > 0) {
                clientExtensions.setExtensionTcpListenPort(clientTcpPort);
            }

            var peerIPAddress = identity.getSocketAddress().getAddress();
            if (peerIPAddress != null) {
                clientExtensions.setExtensionYourIP(peerIPAddress.getAddress());
            }

            var bytes = BencodeEncoder.encodeToBytes(clientExtensions.toHandshake());
            var buffer = ByteBuffer.allocate(1 + bytes.length);
            buffer.put((byte) 0);
            buffer.put(bytes);
            buffer.flip();
            protocol.send(new MessageImpl(MessageType.EXTENDED, buffer));
        }
    }

    @Override
    public void onClose(@NotNull Throwable throwable) {
        swarm.removePeer(this);
    }

}
