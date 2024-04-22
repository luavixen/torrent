package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;

import java.nio.channels.SeekableByteChannel;
import java.util.List;
import java.util.Objects;

public abstract class Storage {

    public abstract class File {

        public abstract @NotNull SeekableByteChannel getChannel();

        private final @NotNull List<@NotNull String> path;
        private final long length;
        private final long offset;

        protected File(@NotNull List<@NotNull String> path, long length, long offset) {
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
            this.path = List.copyOf(path);
            this.length = length;
            this.offset = offset;
        }

        public final @NotNull Storage getStorage() {
            return Storage.this;
        }

        public final @NotNull List<@NotNull String> getPath() {
            return path;
        }

        public final @NotNull String getPathString() {
            return String.join("/", getPath());
        }

        public final @NotNull String getName() {
            var path = getPath();
            return path.get(path.size() - 1);
        }

        public final long getLength() {
            return length;
        }

        public final long getOffset() {
            return offset;
        }

        @Override
        public String toString() {
            return "File{path='" + getPathString() + "', length=" + length + ", offset=" + offset + "}";
        }

    }

    private final @NotNull Info info;
    private final @NotNull List<@NotNull File> files;

    protected Storage(@NotNull Info info) {
        Objects.requireNonNull(info, "Argument 'info'");
        this.info = info;
        this.files = List.copyOf(createFilesFromInfo(info));
    }

    protected abstract @NotNull List<@NotNull File> createFilesFromInfo(@NotNull Info info);

    public final @NotNull Info getInfo() {
        return info;
    }
    public final @NotNull Hash getInfoHash() {
        return info.getHash();
    }

    public final @NotNull List<@NotNull Hash> getPieces() {
        return info.getPieces();
    }

    public final long getPieceLength() {
        return info.getPieceLength();
    }
    public final long getTotalLength() {
        return info.getTotalLength();
    }

    public final @NotNull List<@NotNull File> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        return "Storage{info=" + info + ", files=" + files + "}";
    }

}
