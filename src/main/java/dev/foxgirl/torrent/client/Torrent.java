package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public final class Torrent {

    private final @NotNull Swarm swarm;
    private final @NotNull BitField bitfield;

    public Torrent(@NotNull Swarm swarm, @NotNull BitField bitfield) {
        Objects.requireNonNull(swarm, "Argument 'swarm'");
        Objects.requireNonNull(bitfield, "Argument 'bitfield'");
        this.swarm = swarm;
        this.bitfield = bitfield;
    }

    public Torrent(@NotNull Swarm swarm, @NotNull Info info) {
        this(swarm, new BitField(info));
    }

    public @NotNull Swarm getSwarm() {
        return swarm;
    }

    public @NotNull Info getInfo() {
        return bitfield.getInfo();
    }
    public @NotNull Hash getInfoHash() {
        return getInfo().getHash();
    }

    public @NotNull BitField getBitField() {
        return bitfield;
    }

    @Override
    public @NotNull String toString() {
        return "Torrent{bitfield=" + bitfield + "}";
    }

}
