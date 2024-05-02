package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Client implements AutoCloseable {

    private final @NotNull Identity identity;
    private final @NotNull Extensions extensions;

    private final AtomicBoolean isClosed = new AtomicBoolean();
    private final Map<Hash, Swarm> swarms = new LinkedHashMap<>(32);

    public Client(@NotNull Identity identity) {
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

    public boolean isClosed() {
        return isClosed.get();
    }

    private void assertNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Swarm is closed");
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public synchronized @NotNull List<@NotNull Swarm> getSwarms() {
        return (List) Arrays.asList(swarms.values().toArray());
    }

    public synchronized @Nullable Swarm getSwarm(@Nullable Hash infoHash) {
        if (infoHash == null) {
            return null;
        }
        return swarms.get(infoHash);
    }

    public synchronized @NotNull Swarm createSwarm(@NotNull Info info) {
        Objects.requireNonNull(info, "Argument 'info'");
        assertNotClosed();
        var swarm = swarms.get(info.getInfoHash());
        return swarm != null ? swarm : new Swarm(this, info);
    }

    public synchronized boolean addSwarm(@NotNull Swarm swarm) {
        Objects.requireNonNull(swarm, "Argument 'swarm'");
        assertNotClosed();
        return swarms.putIfAbsent(swarm.getInfoHash(), swarm) == null;
    }

    public synchronized void removeSwarm(@NotNull Swarm swarm) {
        Objects.requireNonNull(swarm, "Argument 'swarm'");
        if (!isClosed()) {
            swarms.remove(swarm.getInfoHash());
        }
    }

    @Override
    public synchronized void close() {
        if (isClosed.getAndSet(true)) {
            return;
        }
        for (var swarm : getSwarms()) {
            swarm.close();
        }
        swarms.clear();
    }

    @Override
    public @NotNull String toString() {
        return "Client{identity=" + identity + ", swarms=" + swarms.size() + ", isClosed=" + isClosed() + "}";
    }

}
