package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Swarm implements AutoCloseable {

    private final @NotNull Client client;
    private final @NotNull BitField bitfield;

    private final AtomicBoolean isClosed = new AtomicBoolean();
    private final Set<Peer> peers = new LinkedHashSet<>(32);

    public Swarm(@NotNull Client client, @NotNull Info info) {
        Objects.requireNonNull(client, "Argument 'client'");
        Objects.requireNonNull(info, "Argument 'info'");
        this.client = client;
        this.bitfield = new BitField(info);
        if (!client.addSwarm(this)) {
            throw new IllegalStateException("Swarm already exists");
        }
    }

    public @NotNull Client getClient() {
        return client;
    }
    public @NotNull BitField getBitField() {
        return bitfield;
    }

    public @NotNull Info getInfo() {
        return bitfield.getInfo();
    }
    public @NotNull Hash getInfoHash() {
        return getInfo().getInfoHash();
    }

    public boolean isClosed() {
        return isClosed.get();
    }

    private void assertNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Swarm is closed");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public @NotNull List<@NotNull Peer> getPeers() {
        return (List) Arrays.asList(peers.toArray());
    }

    public synchronized void addPeer(@NotNull Peer peer) {
        Objects.requireNonNull(peer, "Argument 'peer'");
        assertNotClosed();
        peers.add(peer);
    }

    public synchronized void removePeer(@NotNull Peer peer) {
        Objects.requireNonNull(peer, "Argument 'peer'");
        if (!isClosed()) {
            peers.remove(peer);
        }
    }

    @Override
    public synchronized void close() {
        if (isClosed.getAndSet(true)) {
            return;
        }
        for (var peer : getPeers()) {
            peer.close();
        }
        peers.clear();
        client.removeSwarm(this);
    }

    @Override
    public @NotNull String toString() {
        return "Swarm{infoHash=" + getInfoHash() + ", peers=" + peers.size() + ", isClosed=" + isClosed() + "}";
    }

}
