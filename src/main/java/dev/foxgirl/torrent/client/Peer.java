package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.bencode.BencodeDecoder;
import dev.foxgirl.torrent.bencode.BencodeEncoder;
import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.DefaultExecutors;
import dev.foxgirl.torrent.util.Hash;
import dev.foxgirl.torrent.util.IO;
import dev.foxgirl.torrent.util.Timeout;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class Peer implements Protocol.Listener, AutoCloseable {

    private final @NotNull Swarm swarm;
    private final @NotNull Protocol protocol;

    private final Object lock = new Object();

    private boolean isReady = false;

    private Torrent torrent;

    private BitField bitfield;

    private RandomAccessFile outputFileForTesting;

    private boolean isClientChoking = true;
    private boolean isClientInterested = false;
    private boolean isPeerChoking = true;
    private boolean isPeerInterested = false;

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

    public @NotNull Torrent getTorrent() {
        synchronized (lock) {
            return torrent;
        }
    }
    public @NotNull Info getInfo() {
        return getTorrent().getInfo();
    }

    public @NotNull BitField getClientBitField() {
        return getTorrent().getBitField();
    }
    public @NotNull BitField getPeerBitField() {
        synchronized (lock) {
            return bitfield;
        }
    }

    public boolean isReady() {
        return isReady;
    }

    @Override
    public void close() {
        protocol.close();
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Protocol.class);

    private void setup(Torrent torrent) {
        synchronized (lock) {
            this.isReady = true;
            this.torrent = torrent;
            this.bitfield = new BitField(torrent.getInfo());
            try {
                outputFileForTesting = new RandomAccessFile(torrent.getInfo().getName(), "rw");
                outputFileForTesting.setLength(torrent.getInfo().getTotalLength());
            } catch (IOException cause) {
                throw new RuntimeException(cause);
            }
        }
        LOGGER.info("Peer {} ready with infohash {}", getPeerIdentity(), torrent.getInfoHash());
    }

    public @NotNull CompletableFuture<Void> downloadTest() {
        return CompletableFuture
                .completedFuture(null)
                .thenCompose((ignored) -> {
                    var future = new CompletableFuture<Void>(); new Timeout(() -> future.complete(null)).start(2000);
                    return future;
                })
                .thenCompose((ignored) -> setChoking(false))
                .thenCompose((ignored) -> setInterested(true))
                .thenCompose((ignored) -> CompletableFuture.runAsync(() -> {
                    while (true) {
                        synchronized (lock) {
                            if (!isPeerChoking) return;
                        }
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException cause) {
                            throw new RuntimeException(cause);
                        }
                    }
                }, DefaultExecutors.getIOExecutor()))
                .thenCompose((ignored) -> {
                    var futures = new ArrayList<CompletableFuture<Void>>();
                    for (int i = 0; i < getInfo().getPieceCount(); i++) {
                        int pieceLength;
                        if (i == getInfo().getPieceCount() - 1) {
                            pieceLength = (int) (getInfo().getTotalLength() % getInfo().getPieceLength());
                        } else {
                            pieceLength = (int) (getInfo().getPieceLength());
                        }
                        int pieceOffset = 0;
                        while (pieceOffset < pieceLength) {
                            int blockLength = Math.min(16384, pieceLength - pieceOffset);
                            var payload = ByteBuffer.allocate(12).putInt(i).putInt(pieceOffset).putInt(blockLength).flip();
                            futures.add(protocol.send(new MessageImpl(MessageType.REQUEST, payload)));
                            pieceOffset += blockLength;
                        }
                    }
                    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                });
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
                    int pieceIndex = message.getPayload().getInt();
                    if (pieceIndex < 0 || pieceIndex >= getInfo().getPieceCount()) {
                        throw new IllegalStateException("Invalid piece index, expected [0, " + getInfo().getPieceCount() + "), actual " + pieceIndex);
                    }
                    bitfield.set(pieceIndex);
                    LOGGER.debug("Peer {} has piece {}, {}%", getPeerIdentity(), pieceIndex, bitfield.getPercentageInteger());
                }
                case BITFIELD -> {
                    int expectedByteCount = (getInfo().getPieceCount() + 7) / 8;
                    int actualByteCount = message.getPayload().remaining();
                    if (actualByteCount != expectedByteCount) {
                        throw new IllegalStateException("Invalid bitfield length, expected " + expectedByteCount + ", actual " + actualByteCount);
                    }
                    bitfield.or(BitSet.valueOf(message.getPayload()));
                    LOGGER.debug("Peer {} updated bitfield, {}%", getPeerIdentity(), bitfield.getPercentageInteger());
                }
                case HAVE_ALL -> {
                    bitfield.setAll();
                    LOGGER.debug("Peer {} has all pieces", getPeerIdentity());
                }
                case HAVE_NONE -> {
                    bitfield.clearAll();
                    LOGGER.debug("Peer {} has no pieces", getPeerIdentity());
                }
                case EXTENDED -> {
                    var messageID = message.getPayload().get();
                    if (messageID == 0) {
                        var handshakeStream = IO.getInputStream(message.getPayload());
                        var handshake = BencodeDecoder.decodeFromStream(handshakeStream);
                        getPeerExtensions().fromHandshake(handshake);
                        LOGGER.debug("Peer {} received extended handshake: {}", getPeerIdentity(), handshake);
                    } else {
                        LOGGER.warn("Peer {} received unknown extended message {}", getPeerIdentity(), messageID);
                    }
                }
                case PIECE -> {
                    var index = message.getPayload().getInt();
                    var offset = message.getPayload().getInt();
                    var data = IO.getArray(message.getPayload());

                    LOGGER.debug("Peer {} received piece {}, offset {}, length {}", getPeerIdentity(), index, offset, data.length);

                    outputFileForTesting.seek(getInfo().getPieceLength() * index + offset);
                    outputFileForTesting.write(data);
                }
            }
        }
    }

    @Override
    public void onConnect(@NotNull Identity identity) {
        swarm.addPeer(this);

        var torrent = swarm.getTorrent(protocol.getInfoHash());
        if (torrent == null) {
            // TODO
            throw new IllegalStateException("Peers with torrents that are not in the swarm are not supported yet");
        } else {
            setup(torrent);
        }

        if (getPeerExtensions().hasExtensionProtocol() && getClientExtensions().hasExtensionProtocol()) {
            LOGGER.debug("Peer {} supports extension protocol", getPeerIdentity());

            var clientExtensions = getClientExtensions();

            var clientTcpPort = identity.getSocketAddress().getPort();
            if (clientTcpPort > 0) {
                clientExtensions.setExtensionTcpListenPort(clientTcpPort);
            }

            var peerIPAddress = identity.getSocketAddress().getAddress();
            if (peerIPAddress != null) {
                clientExtensions.setExtensionYourIP(peerIPAddress.getAddress());
            }

            var handshake = clientExtensions.toHandshake();
            var handshakeBytes = BencodeEncoder.encodeToBytes(handshake);

            LOGGER.debug("Peer {} sending extension handshake: {}", getPeerIdentity(), handshake);

            var buffer = ByteBuffer.allocate(1 + handshakeBytes.length);
            buffer.put((byte) 0);
            buffer.put(handshakeBytes);
            buffer.flip();
            protocol.send(new MessageImpl(MessageType.EXTENDED, buffer));
        }

        if (getPeerExtensions().hasFastPeers() && getClientExtensions().hasFastPeers()) {
            LOGGER.debug("Peer {} supports fast peers, sending bitfield", getPeerIdentity());
            if (torrent.getBitField().isComplete()) {
                protocol.send(new MessageImpl(MessageType.HAVE_ALL));
            } else if (torrent.getBitField().isEmpty()) {
                protocol.send(new MessageImpl(MessageType.HAVE_NONE));
            } else {
                protocol.send(new MessageImpl(MessageType.BITFIELD, ByteBuffer.wrap(torrent.getBitField().toArray())));
            }
        } else {
            LOGGER.debug("Peer {} sending bitfield", getPeerIdentity());
            protocol.send(new MessageImpl(MessageType.BITFIELD, ByteBuffer.wrap(torrent.getBitField().toArray())));
        }

    }

    @Override
    public void onClose(@NotNull Throwable throwable) {
        swarm.removePeer(this);

        synchronized (lock) {
            isReady = false;
        }
    }

    @Override
    public @NotNull String toString() {
        return "Peer{protocol=" + getProtocol() + ", isReady=" + isReady + "}";
    }

}
