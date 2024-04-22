package dev.foxgirl.torrent.bencode;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Objects;

public final class BencodeDecoder implements Closeable {

    public static @NotNull BencodeElement decodeFromStream(@NotNull InputStream inputStream) throws IOException {
        Objects.requireNonNull(inputStream, "Argument 'inputStream'");
        return new BencodeDecoder(inputStream).decode();
    }

    public static @NotNull BencodeElement decodeFromBytes(byte @NotNull [] bytes) throws IOException {
        Objects.requireNonNull(bytes, "Argument 'bytes'");
        try (var inputStream = new ByteArrayInputStream(bytes)) {
            return decodeFromStream(inputStream);
        }
    }

    private InputStream inputStream;

    public BencodeDecoder(@NotNull InputStream inputStream) {
        Objects.requireNonNull(inputStream, "Argument 'inputStream'");
        this.inputStream = inputStream;
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
        inputStream = null;
    }

    private int read() throws IOException {
        int value = inputStream.read();
        if (value < 0) {
            throw new EOFException("Unexpected end of stream");
        }
        return value;
    }
    private byte[] read(int length) throws IOException {
        var bytes = new byte[length];
        int total = 0;
        while (total < length) {
            int count = inputStream.read(bytes, total, length - total);
            if (count < 0) {
                throw new EOFException("Unexpected end of stream");
            }
            total += count;
        }
        return bytes;
    }

    public @NotNull BencodeElement decode() throws IOException {
        try {
            return decodeNext(read());
        } catch (StackOverflowError cause) {
            throw new BencodeDecodeException("Stack overflow", cause);
        }
    }

    private BencodeElement decodeNext(int c) throws IOException {
        if (c >= '0' && c <= '9') {
            return decodeNextString(c);
        } else if (c == 'i') {
            return decodeNextInteger();
        } else if (c == 'l') {
            return decodeNextList();
        } else if (c == 'd') {
            return decodeNextMap();
        } else {
            throw new BencodeDecodeException("Unexpected start of element: " + (char) c);
        }
    }

    private BencodeString decodeNextString(int c) throws IOException {
        int length = 0;
        while (true) {
            length = length * 10 + (c - '0');
            if (length < 0) {
                throw new BencodeDecodeException("String length overflow");
            }
            c = read();
            if (c == ':') {
                break;
            }
            if (c < '0' || c > '9') {
                throw new BencodeDecodeException("String length invalid");
            }
        }
        return BencodeString.wrap(read(length));
    }

    private BencodeInteger decodeNextInteger() throws IOException {
        int c = read();

        boolean negative = false;
        if (c == '-') {
            c = read();
            negative = true;
        }

        long value = 0;
        while (c != 'e') {
            if (c < '0' || c > '9') {
                throw new BencodeDecodeException("Invalid integer format");
            }
            value = value * 10 + (c - '0');
            if (value < 0) {
                throw new BencodeDecodeException("Integer overflow");
            }
            c = read();
        }

        return BencodeInteger.of(negative ? -value : value);
    }

    private BencodeList decodeNextList() throws IOException {
        BencodeList list = new BencodeList();
        while (true) {
            int c = read();
            if (c == 'e') {
                break;
            }
            list.add(decodeNext(c));
        }
        return list;
    }

    private BencodeMap decodeNextMap() throws IOException {
        BencodeMap map = new BencodeMap();
        while (true) {
            int c = read();
            if (c == 'e') {
                break;
            }
            BencodeElement key = decodeNext(c);
            if (key.getType() != BencodeType.STRING) {
                throw new BencodeDecodeException("Map key is not a string");
            }
            BencodeElement value = decodeNext(read());
            if (map.putIfAbsent(key.asString(), value) != null) {
                throw new BencodeDecodeException("Duplicate map key: " + key);
            }
        }
        return map;
    }

}
