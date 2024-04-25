package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import org.jetbrains.annotations.NotNull;

import java.nio.channels.AsynchronousByteChannel;
import java.util.BitSet;
import java.util.Objects;

public final class Peer implements Protocol.Listener, AutoCloseable {

    private final @NotNull Swarm swarm;
    private final @NotNull Protocol protocol;

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

    @Override
    public void close() {
        protocol.close();
    }

    @Override
    public synchronized void onReceive(@NotNull MessageImpl message) {
        switch (message.getType()) {
            case CHOKE -> isPeerChoking = true;
            case UNCHOKE -> isPeerChoking = false;
            case INTERESTED -> isPeerInterested = true;
            case NOT_INTERESTED -> isPeerInterested = false;
            case HAVE -> {
                int pieceIndex = message.getPayload().getInt();
                if (pieceIndex < 0 || pieceIndex >= info.getPieceCount()) {
                    throw new IllegalStateException("Invalid piece index, expected [0, " + info.getPieceCount() + "), actual " + pieceIndex);
                }
                peerBitfield.set(pieceIndex);
            }
            case BITFIELD -> {
                int expectedByteCount = (info.getPieceCount() + 7) / 8;
                int actualByteCount = message.getPayload().remaining();
                if (actualByteCount != expectedByteCount) {
                    throw new IllegalStateException("Invalid bitfield length, expected " + expectedByteCount + ", actual " + actualByteCount);
                }
                peerBitfield.or(BitSet.valueOf(message.getPayload()));
            }
        }
    }

    @Override
    public void onConnect(@NotNull Identity identity) {
        var info = swarm.getInfo(protocol.getInfoHash());
        if (info == null) {
            close();
            return;
        }
        this.info = info;

        peerBitfield = new BitSet(info.getPieceCount());
        clientBitfield = new BitSet(info.getPieceCount());

        swarm.addPeer(this);
    }

    @Override
    public void onClose(@NotNull Throwable throwable) {
        swarm.removePeer(this);
    }

}
