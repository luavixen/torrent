package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class Swarm {

    private final @NotNull Identity identity;

    private final Object lock = new Object();

    private final Set<Peer> peers = new LinkedHashSet<>(200);
    private final Map<Hash, Info> torrents = new HashMap<>(32);

    public Swarm(@NotNull Identity identity) {
        Objects.requireNonNull(identity, "Argument 'identity'");
        this.identity = identity;
    }

    public @NotNull Identity getIdentity() {
        return identity;
    }

    public @Nullable Info getInfo(@Nullable Hash infoHash) {
        if (infoHash == null) {
            return null;
        }
        synchronized (lock) {
            return torrents.get(infoHash);
        }
    }

    public boolean addTorrent(@NotNull Info info) {
        Objects.requireNonNull(info, "Argument 'info'");
        synchronized (lock) {
            return torrents.put(info.getHash(), info) == null;
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

}
