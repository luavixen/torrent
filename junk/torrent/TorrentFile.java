package dev.foxgirl.torrent.client.torrent;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;

public final class TorrentFile {

    private final @NotNull TorrentData torrent;
    private final @NotNull List<@NotNull String> path;
    private final long length;
    private final long offset;

    TorrentFile(@NotNull TorrentData torrent, @NotNull List<@NotNull String> path, long length, long offset) {
        Objects.requireNonNull(torrent, "Argument 'torrent'");
        Objects.requireNonNull(path, "Argument 'path'");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("Path is empty");
        }
        if (length < 0) {
            throw new IllegalArgumentException("Length is negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("Offset is negative");
        }
        this.torrent = torrent;
        this.path = path;
        this.length = length;
        this.offset = offset;
    }

    public @NotNull TorrentData getTorrent() {
        return torrent;
    }

    public @NotNull List<@NotNull String> getPath() {
        return path;
    }

    public @NotNull String getPathString() {
        return String.join("/", path);
    }

    public @NotNull String getName() {
        return path.get(path.size() - 1);
    }

    public long getLength() {
        return length;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return "TorrentFile{path=" + path + ", length=" + length + ", offset=" + offset + "}";
    }

}
