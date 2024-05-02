package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.bencode.BencodeDecoder;
import dev.foxgirl.torrent.bencode.BencodeEncoder;
import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import dev.foxgirl.torrent.util.IO;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.util.BitSet;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class Peer implements Protocol.Listener, AutoCloseable {

    private final @NotNull Client client;
    private final @NotNull Protocol protocol;

    private final Object lock = new Object();

    private boolean isReady = false;

    private Swarm swarm;

    private BitField clientBitfield;
    private BitField peerBitfield;

    private boolean isClientChoking = true;
    private boolean isClientInterested = false;
    private boolean isPeerChoking = true;
    private boolean isPeerInterested = false;

    public Peer(@NotNull Client client, @NotNull AsynchronousByteChannel channel) {
        Objects.requireNonNull(client, "Argument 'client'");
        Objects.requireNonNull(channel, "Argument 'channel'");
        this.client = client;
        this.protocol = new Protocol(channel, this);
    }

    public @NotNull CompletableFuture<@NotNull Identity> establishOutgoing(
            @NotNull InetSocketAddress peerAddress,
            @NotNull Hash infoHash
    ) {
        return protocol.establishOutgoing(getClientIdentity(), getClientExtensions(), peerAddress, infoHash);
    }
    public @NotNull CompletableFuture<@NotNull Identity> establishIncoming(
            @NotNull InetSocketAddress peerAddress
    ) {
        return protocol.establishIncoming(getClientIdentity(), getClientExtensions(), peerAddress);
    }

    public @NotNull Client getClient() {
        return client;
    }
    public @NotNull Protocol getProtocol() {
        return protocol;
    }

    public @NotNull Identity getClientIdentity() {
        return client.getIdentity();
    }
    public @NotNull Identity getPeerIdentity() {
        var identity = protocol.getIdentity();
        if (identity == null) {
            throw new IllegalStateException("Peer is not connected");
        }
        return identity;
    }

    public @NotNull Extensions getClientExtensions() {
        return client.getExtensions();
    }
    public @NotNull Extensions getPeerExtensions() {
        var extensions = protocol.getExtensions();
        if (extensions == null) {
            throw new IllegalStateException("Peer is not connected");
        }
        return extensions;
    }

    public boolean isReady() {
        synchronized (lock) {
            return isReady;
        }
    }

    private void assertReady() {
        if (!isReady()) {
            throw new IllegalStateException("Peer is not ready");
        }
    }

    public @NotNull Swarm getSwarm() {
        synchronized (lock) {
            assertReady();
            return Objects.requireNonNull(swarm, "Field 'swarm'");
        }
    }

    public @NotNull Info getInfo() {
        return getSwarm().getInfo();
    }

    public @NotNull BitField getClientBitfield() {
        synchronized (lock) {
            assertReady();
            return Objects.requireNonNull(clientBitfield, "Field 'clientBitfield'");
        }
    }
    public @NotNull BitField getPeerBitfield() {
        synchronized (lock) {
            assertReady();
            return Objects.requireNonNull(peerBitfield, "Field 'peerBitfield'");
        }
    }

    public boolean isClientChoking() {
        synchronized (lock) { assertReady(); return isClientChoking; }
    }
    public boolean isClientInterested() {
        synchronized (lock) { assertReady(); return isClientInterested; }
    }
    public boolean isPeerChoking() {
        synchronized (lock) { assertReady(); return isPeerChoking; }
    }
    public boolean isPeerInterested() {
        synchronized (lock) { assertReady(); return isPeerInterested; }
    }

    @Override
    public void close() {
        protocol.close();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Protocol.class);

    private boolean supportsFastPeers() {
        return getPeerExtensions().hasFastPeers() && getClientExtensions().hasFastPeers();
    }
    private boolean supportsExtensionProtocol() {
        return getPeerExtensions().hasExtensionProtocol() && getClientExtensions().hasExtensionProtocol();
    }

    private void assertFastPeers() {
        if (!supportsFastPeers()) {
            throw new IllegalStateException("Peer does not support fast peers");
        }
    }
    private void assertExtensionProtocol() {
        if (!supportsExtensionProtocol()) {
            throw new IllegalStateException("Peer does not support extension protocol");
        }
    }

    private void setup(@NotNull Swarm swarm) {
        synchronized (lock) {
            isReady = true;
            this.swarm = swarm;
            this.clientBitfield = swarm.getBitField();
            this.peerBitfield = new BitField(swarm.getInfo());
        }
        swarm.addPeer(this);
        LOGGER.info("Peer {} ready with infohash {}", getPeerIdentity(), swarm.getInfoHash());
    }

    private @NotNull CompletableFuture<Void> setChoking(boolean isChoking) {
        synchronized (lock) {
            if (isClientChoking != isChoking) {
                isClientChoking = isChoking;
                LOGGER.debug("Peer {} setting to {}", getPeerIdentity(), isChoking ? "choked" : "unchoked");
                return protocol.send(new MessageImpl(isChoking ? MessageType.CHOKE : MessageType.UNCHOKE));
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }
    }
    private @NotNull CompletableFuture<Void> setInterested(boolean isInterested) {
        synchronized (lock) {
            if (isClientInterested != isInterested) {
                isClientInterested = isInterested;
                LOGGER.debug("Peer {} setting to {}", getPeerIdentity(), isInterested ? "interested" : "not interested");
                return protocol.send(new MessageImpl(isInterested ? MessageType.INTERESTED : MessageType.NOT_INTERESTED));
            } else {
                return CompletableFuture.completedFuture(null);
            }
        }
    }

    @Override
    public void onReceive(@NotNull MessageImpl message) throws Exception {
        synchronized (lock) {
            switch (message.getType()) {
                case CHOKE -> {
                    isPeerChoking = true;
                    LOGGER.debug("Peer {} choked us", getPeerIdentity());
                }
                case UNCHOKE -> {
                    isPeerChoking = false;
                    LOGGER.debug("Peer {} unchoked us", getPeerIdentity());
                }
                case INTERESTED -> {
                    isPeerInterested = true;
                    LOGGER.debug("Peer {} interested in us", getPeerIdentity());
                }
                case NOT_INTERESTED -> {
                    isPeerInterested = false;
                    LOGGER.debug("Peer {} not interested in us", getPeerIdentity());
                }
                case HAVE -> {
                    assertReady();
                    int pieceIndex = message.getPayload().getInt();
                    if (pieceIndex < 0 || pieceIndex >= getInfo().getPieceCount()) {
                        throw new IllegalStateException("Invalid piece index, expected [0, " + getInfo().getPieceCount() + "), actual " + pieceIndex);
                    }
                    peerBitfield.set(pieceIndex);
                    LOGGER.debug("Peer {} has piece {}, {}%", getPeerIdentity(), pieceIndex, peerBitfield.getPercentageInteger());
                }
                case BITFIELD -> {
                    assertReady();
                    int expectedByteCount = peerBitfield.byteLength();
                    int actualByteCount = message.getPayload().remaining();
                    if (actualByteCount != expectedByteCount) {
                        throw new IllegalStateException("Invalid bitfield length, expected " + expectedByteCount + ", actual " + actualByteCount);
                    }
                    peerBitfield.or(BitSet.valueOf(message.getPayload()));
                    LOGGER.debug("Peer {} updated bitfield, {}%", getPeerIdentity(), peerBitfield.getPercentageInteger());
                }
                case HAVE_ALL -> {
                    assertReady(); assertFastPeers();
                    peerBitfield.setAll();
                    LOGGER.debug("Peer {} has all pieces", getPeerIdentity());
                }
                case HAVE_NONE -> {
                    assertReady(); assertFastPeers();
                    peerBitfield.clearAll();
                    LOGGER.debug("Peer {} has no pieces", getPeerIdentity());
                }
                case EXTENDED -> {
                    assertExtensionProtocol();
                    var messageID = message.getPayload().get();
                    if (messageID == 0) {
                        var handshake = BencodeDecoder.decodeFromStream(IO.getInputStream(message.getPayload()));
                        getPeerExtensions().fromHandshake(handshake);
                        LOGGER.debug("Peer {} received extended handshake: {}", getPeerIdentity(), handshake);
                    } else {
                        LOGGER.warn("Peer {} received unknown extended message {}", getPeerIdentity(), messageID);
                    }
                }
            }
        }
    }

    @Override
    public void onConnect(@NotNull Identity identity) {
        var swarm = client.getSwarm(protocol.getInfoHash());
        if (swarm == null) {
            // TODO
            throw new IllegalStateException("Peers with torrents that are not in the swarm are not supported yet");
        } else {
            setup(swarm);
        }

        if (supportsExtensionProtocol()) {
            LOGGER.debug("Peer {} supports extension protocol", getPeerIdentity());

            var clientExtensions = getClientExtensions().copyMutable();

            var clientTcpPort = identity.getSocketAddress().getPort();
            if (clientTcpPort > 0) {
                clientExtensions.setExtensionTcpListenPort(clientTcpPort);
            }

            var peerIPAddress = identity.getSocketAddress().getAddress();
            if (peerIPAddress != null) {
                clientExtensions.setExtensionYourIP(peerIPAddress.getAddress());
            }

            try (var stream = new ByteArrayOutputStream(256)) {
                stream.write(0);

                var handshake = clientExtensions.toHandshake();
                BencodeEncoder.encodeToStream(handshake, stream);

                LOGGER.debug("Peer {} sending extension handshake: {}", getPeerIdentity(), handshake);

                protocol.send(new MessageImpl(MessageType.EXTENDED, ByteBuffer.wrap(stream.toByteArray())));
            } catch (IOException cause) {
                throw new RuntimeException(cause);
            }
        }

        if (supportsFastPeers()) {
            LOGGER.debug("Peer {} supports fast peers, sending bitfield", getPeerIdentity());
            if (clientBitfield.isComplete()) {
                protocol.send(new MessageImpl(MessageType.HAVE_ALL));
            } else if (clientBitfield.isEmpty()) {
                protocol.send(new MessageImpl(MessageType.HAVE_NONE));
            } else {
                protocol.send(new MessageImpl(MessageType.BITFIELD, ByteBuffer.wrap(clientBitfield.toArray())));
            }
        } else {
            LOGGER.debug("Peer {} sending bitfield", getPeerIdentity());
            protocol.send(new MessageImpl(MessageType.BITFIELD, ByteBuffer.wrap(clientBitfield.toArray())));
        }
    }

    @Override
    public void onClose(@NotNull Throwable throwable) {
        Swarm swarm;
        synchronized (lock) {
            isReady = false;
            swarm = this.swarm;
        }
        if (swarm != null) {
            swarm.removePeer(this);
        }
    }

    @Override
    public @NotNull String toString() {
        return "Peer{protocol=" + getProtocol() + ", isReady=" + isReady + "}";
    }

}
