package dev.foxgirl.torrent.client.torrent;

import dev.foxgirl.torrent.metainfo.Info;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class TorrentData {

    private final @NotNull Info info;
    private final @NotNull List<@NotNull TorrentFile> files;

    public TorrentData(@NotNull Info info) {
        Objects.requireNonNull(info, "Argument 'info'");
        this.info = info;
        this.files = filesFromInfo(info);
    }

    private @NotNull List<@NotNull TorrentFile> filesFromInfo(@NotNull Info info) {
        var files = new ArrayList<TorrentFile>(info.getFiles().size());
        var offset = 0L;
        for (var file : info.getFiles()) {
            files.add(new TorrentFile(this, file.getPath(), file.getLength(), offset));
            offset += file.getLength();
        }
        return List.copyOf(files);
    }

    public @NotNull Info getInfo() {
        return info;
    }
    public @NotNull Hash getInfoHash() {
        return info.getHash();
    }

    public @NotNull List<@NotNull Hash> getPieces() {
        return info.getPieces();
    }

    public long getPieceLength() {
        return info.getPieceLength();
    }
    public long getTotalLength() {
        return info.getTotalLength();
    }

    public @NotNull List<@NotNull TorrentFile> getFiles() {
        return files;
    }

    @Override
    public String toString() {
        return "TorrentData{info=" + info + ", files=" + files + "}";
    }

}
