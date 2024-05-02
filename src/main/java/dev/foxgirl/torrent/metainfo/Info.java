package dev.foxgirl.torrent.metainfo;

import dev.foxgirl.torrent.bencode.*;
import dev.foxgirl.torrent.util.Hash;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;

public final class Info extends InfoContainer {

    public static @NotNull Info fromBencode(BencodeElement source) throws InvalidBencodeException {
        return fromBencode(source, null);
    }

    public static @NotNull Info fromBencode(BencodeElement source, @Nullable Hash infohash) throws InvalidBencodeException {
        if (source == null) {
            throw new InvalidBencodeException("Info is null");
        }
        if (!source.getType().isMap()) {
            throw new InvalidBencodeException("Info is not a map");
        }

        if (infohash == null) {
            infohash = calculateInfoHash(source);
        }

        var map = new BencodeMap(source.asMap());

        var nameValue = map.remove("name");
        if (nameValue == null) {
            throw new InvalidBencodeException("Info name is missing");
        }
        if (!nameValue.getType().isString()) {
            throw new InvalidBencodeException("Info name is not a string");
        }

        var name = nameValue.asString().getValue();

        var piecesValue = map.remove("pieces");
        if (piecesValue == null) {
            throw new InvalidBencodeException("Info pieces is missing");
        }
        if (!piecesValue.getType().isString()) {
            throw new InvalidBencodeException("Info pieces is not a string");
        }

        var pieces = new ArrayList<Hash>();
        var piecesBuffer = piecesValue.asString().toBuffer();
        if (piecesBuffer.remaining() % 20 != 0) {
            throw new InvalidBencodeException("Info pieces length is not a multiple of 20");
        }
        var pieceHash = new byte[20];
        while (piecesBuffer.hasRemaining()) {
            piecesBuffer.get(pieceHash);
            pieces.add(Hash.of(pieceHash));
        }

        var pieceLengthValue = map.remove("piece length");
        if (pieceLengthValue == null) {
            throw new InvalidBencodeException("Info piece length is missing");
        }
        if (!pieceLengthValue.getType().isInteger()) {
            throw new InvalidBencodeException("Info piece length is not an integer");
        }

        var pieceLength = pieceLengthValue.asInteger().getValue();

        Boolean isPrivate = null;
        var isPrivateValue = map.remove("private");
        if (isPrivateValue != null) {
            if (!isPrivateValue.getType().isInteger()) {
                throw new InvalidBencodeException("Info private is not an integer");
            }
            var isPrivateInteger = isPrivateValue.asInteger().getValue();
            if (isPrivateInteger != 0 && isPrivateInteger != 1) {
                throw new InvalidBencodeException("Info private is not a boolean");
            }
            isPrivate = isPrivateInteger == 1;
        }

        if (!map.containsKey("files")) {

            var lengthValue = map.remove("length");
            if (lengthValue == null) {
                throw new InvalidBencodeException("Info length is missing");
            }
            if (!lengthValue.getType().isInteger()) {
                throw new InvalidBencodeException("Info length is not an integer");
            }

            var length = lengthValue.asInteger().getValue();

            return new Info(name, length, pieces, pieceLength, isPrivate, map, infohash);

        } else {

            var filesValue = map.remove("files");
            if (filesValue == null) {
                throw new InvalidBencodeException("Info files is missing");
            }
            if (!filesValue.getType().isList()) {
                throw new InvalidBencodeException("Info files is not a list");
            }

            var files = new ArrayList<FileInfo>(filesValue.asList().size());
            for (var filesValueElement : filesValue.asList()) {
                files.add(FileInfo.fromBencode(filesValueElement));
            }

            return new Info(name, pieces, pieceLength, files, isPrivate, map, infohash);

        }
    }

    private final @NotNull String name;
    private final @NotNull List<@NotNull Hash> pieces;
    private final long pieceLength;
    private final long totalLength;
    private final @NotNull List<@NotNull FileInfo> files;
    private final boolean isSingleFile;
    private final @Nullable Boolean isPrivate;

    private final @NotNull Hash infoHash;
    private final long infoLength;

    private static final class FileEntry {
        private final FileInfo file;
        private final long offset;

        private FileEntry(FileInfo file, long offset) {
            this.file = file;
            this.offset = offset;
        }
    }

    private final Map<List<String>, FileEntry> fileEntries;

    public Info(
            @NotNull String fileName,
            long fileLength,
            @NotNull List<@NotNull Hash> pieces,
            long pieceLength,
            @Nullable Boolean isPrivate,
            @Nullable BencodeMap extraFields
    ) {
        super(extraFields);

        Objects.requireNonNull(fileName, "Argument 'fileName'");
        Objects.requireNonNull(pieces, "Argument 'pieces'");

        pieces = List.copyOf(pieces);
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("Pieces is empty");
        }

        if (fileLength < 0) {
            throw new IllegalArgumentException("File length is negative");
        }
        if (pieceLength < 0) {
            throw new IllegalArgumentException("Piece length is negative");
        }

        this.name = fileName;
        this.pieces = pieces;
        this.pieceLength = pieceLength;
        this.totalLength = fileLength;
        this.files = List.of(new FileInfo(List.of(fileName), fileLength, null));
        this.isSingleFile = true;
        this.isPrivate = isPrivate;

        var digestOutputStream = calculateInfoHashStream(toBencode());
        this.infoHash = digestOutputStream.complete();
        this.infoLength = digestOutputStream.getCount();

        this.fileEntries = createFileEntries();

        checkPiecesLength();
    }

    public Info(
            @NotNull String name,
            @NotNull List<@NotNull Hash> pieces,
            long pieceLength,
            @NotNull List<@NotNull FileInfo> files,
            @Nullable Boolean isPrivate,
            @Nullable BencodeMap extraFields
    ) {
        super(extraFields);

        Objects.requireNonNull(name, "Argument 'name'");
        Objects.requireNonNull(pieces, "Argument 'pieces'");
        Objects.requireNonNull(files, "Argument 'files'");

        pieces = List.copyOf(pieces);
        if (pieces.isEmpty()) {
            throw new IllegalArgumentException("Pieces is empty");
        }

        files = List.copyOf(files);
        if (files.isEmpty()) {
            throw new IllegalArgumentException("Files is empty");
        }

        if (pieceLength < 0) {
            throw new IllegalArgumentException("Piece length is negative");
        }

        this.name = name;
        this.pieces = pieces;
        this.pieceLength = pieceLength;
        this.totalLength = files.stream().mapToLong(FileInfo::getLength).sum();
        this.files = files;
        this.isSingleFile = false;
        this.isPrivate = isPrivate;

        var digestOutputStream = calculateInfoHashStream(toBencode());
        this.infoHash = digestOutputStream.complete();
        this.infoLength = digestOutputStream.getCount();

        this.fileEntries = createFileEntries();

        checkPiecesLength();
    }

    public Info(
            @NotNull String fileName,
            long fileLength,
            @NotNull List<@NotNull Hash> pieces,
            long pieceLength,
            @Nullable Boolean isPrivate,
            @Nullable BencodeMap extraFields,
            @NotNull Hash infoHash
    ) {
        this(fileName, fileLength, pieces, pieceLength, isPrivate, extraFields);
        checkInfoHash(infoHash);
    }

    public Info(
            @NotNull String name,
            @NotNull List<@NotNull Hash> pieces,
            long pieceLength,
            @NotNull List<@NotNull FileInfo> files,
            @Nullable Boolean isPrivate,
            @Nullable BencodeMap extraFields,
            @NotNull Hash infoHash
    ) {
        this(name, pieces, pieceLength, files, isPrivate, extraFields);
        checkInfoHash(infoHash);
    }

    private static Hash.DigestOutputStream calculateInfoHashStream(BencodeElement element) {
        try (var digestOutputStream = Hash.digestOutputStream(Hash.Algorithm.SHA1)) {
            try (var bufferedOutputStream = new BufferedOutputStream(digestOutputStream, 2048)) {
                BencodeEncoder.encodeToStream(element, bufferedOutputStream);
            }
            return digestOutputStream;
        } catch (IOException cause) {
            throw new RuntimeException("Failed to calculate infohash from Bencode", cause);
        }
    }

    private static Hash calculateInfoHash(BencodeElement element) {
        return calculateInfoHashStream(element).complete();
    }

    private Map<List<String>, FileEntry> createFileEntries() {
        if (files.size() == 1) {
            var file = files.get(0);
            return Map.of(file.getPath(), new FileEntry(file, 0));
        }
        var entries = new HashMap<List<String>, FileEntry>((files.size() >> 2) + (files.size() >> 1));
        var offset = 0L;
        for (var file : files) {
            var previousEntry = entries.putIfAbsent(file.getPath(), new FileEntry(file, offset));
            if (previousEntry != null) {
                throw new IllegalArgumentException("Duplicate file path: " + file.getPathString());
            }
            offset += file.getLength();
        }
        return entries;
    }

    private void checkInfoHash(Hash infoHash) {
        Objects.requireNonNull(infoHash, "Argument 'infoHash'");
        if (!Objects.equals(this.infoHash, infoHash)) {
            throw new IllegalStateException("Provided info hash does not match calculated hash");
        }
    }

    private void checkPiecesLength() {
        if (pieces.size() != (int) StrictMath.ceil((double) totalLength / (double) pieceLength)) {
            throw new IllegalArgumentException("Pieces count does not match file length and piece length");
        }
    }

    @Override
    public @NotNull BencodeMap toBencode() {
        var map = getExtraFields();

        map.putString("name", name);

        var piecesBuffer = ByteBuffer.allocate(pieces.size() * 20);
        for (var hash : pieces) {
            piecesBuffer.put(hash.getBytes());
        }
        map.put("pieces", BencodeString.of(piecesBuffer.flip()));
        map.putInteger("piece length", pieceLength);

        if (isSingleFile) {
            map.putInteger("length", totalLength);
        } else {
            var filesList = new BencodeList();
            for (var file : files) {
                filesList.add(file.toBencode());
            }
            map.put("files", filesList);
        }

        if (isPrivate != null) {
            map.putInteger("private", isPrivate ? 1 : 0);
        }

        return map;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull List<@NotNull Hash> getPieces() {
        return pieces;
    }

    public int getPieceCount() {
        return pieces.size();
    }

    public long getPieceLength() {
        return pieceLength;
    }

    public @NotNull List<@NotNull FileInfo> getFiles() {
        return files;
    }

    public long getTotalLength() {
        return totalLength;
    }

    public boolean isSingleFile() {
        return isSingleFile;
    }

    public @Nullable Boolean isPrivate() {
        return isPrivate;
    }

    public @NotNull Hash getInfoHash() {
        return infoHash;
    }

    public long getInfoLength() {
        return infoLength;
    }

    public int getPieceIndex(long offset) {
        if (offset < 0 || offset >= totalLength) {
            throw new IndexOutOfBoundsException("Offset is out of bounds");
        }
        var pieceIndex = (int) (offset / pieceLength);
        if (pieceIndex < 0 || pieceIndex >= pieces.size()) {
            throw new IllegalStateException("Calculated piece index is invalid");
        }
        return pieceIndex;
    }

    public long getPieceLength(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= pieces.size()) {
            throw new IndexOutOfBoundsException("Piece index is out of bounds");
        }
        return pieceIndex == pieces.size() - 1
                ? totalLength % pieceLength
                : pieceLength;
    }

    public long getFileOffset(@NotNull FileInfo file) {
        Objects.requireNonNull(file, "Argument 'file'");
        return getFileOffset(file.getPath());
    }
    public long getFileOffset(@NotNull List<String> path) {
        Objects.requireNonNull(path, "Argument 'path'");
        var fileEntry = fileEntries.get(path);
        if (fileEntry == null) {
            throw new IllegalArgumentException("File not found in info");
        }
        return fileEntry.offset;
    }

    @Override
    public @NotNull String toString() {
        return new StringJoiner(", ", "Info{", "}")
                .add("name='" + name + "'")
                .add("pieces=" + pieces.size())
                .add("pieceLength=" + pieceLength)
                .add("totalLength=" + totalLength)
                .add("files=" + files)
                .add("isSingleFile=" + isSingleFile)
                .add("infoHash=" + infoHash)
                .add("infoLength=" + infoLength)
                .toString();
    }

}
