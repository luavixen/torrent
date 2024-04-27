package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Swarm {

    private final @NotNull Identity identity;
    private final @NotNull Extensions extensions;

    private final Object lock = new Object();

    private final Set<Peer> peers = new LinkedHashSet<>(200);
    private final Map<Hash, BitField> torrents = new LinkedHashMap<>(32);

    public Swarm(@NotNull Identity identity) {
        Objects.requireNonNull(identity, "Argument 'identity'");
        this.identity = identity;
        this.extensions = Extensions.getSupportedExtensions();
    }

    public @NotNull Identity getIdentity() {
        return identity;
    }
    public @NotNull Extensions getExtensions() {
        return extensions.copyImmutable();
    }

    public @Nullable BitField getTorrent(@Nullable Hash infoHash) {
        if (infoHash == null) {
            return null;
        }
        synchronized (lock) {
            return torrents.get(infoHash);
        }
    }

    public @NotNull BitField addTorrent(@NotNull Info info) {
        Objects.requireNonNull(info, "Argument 'info'");
        synchronized (lock) {
            return torrents.computeIfAbsent(info.getHash(), (hash) -> new BitField(info));
        }
    }

    public boolean addPeer(@NotNull Peer peer) {
        Objects.requireNonNull(peer, "Argument 'peer'");
        synchronized (lock) {
            return peers.add(peer);
        }
    }
    public boolean removePeer(@NotNull Peer peer) {
        Objects.requireNonNull(peer, "Argument 'peer'");
        synchronized (lock) {
            return peers.remove(peer);
        }
    }

    @Override
    public @NotNull String toString() {
        return String.format(
            "Swarm{identity=%s, peers=%d, torrents=%d}",
            identity, peers.size(), torrents.size()
        );
    }

}
