package dev.foxgirl.torrent.bencode;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class BencodeEncoder implements Closeable {

    public static void encodeToStream(@NotNull BencodeElement element, @NotNull OutputStream outputStream) throws IOException {
        Objects.requireNonNull(element, "Argument 'element'");
        Objects.requireNonNull(outputStream, "Argument 'outputStream'");
        new BencodeEncoder(outputStream).encode(element);
    }

    public static byte @NotNull [] encodeToBytes(@NotNull BencodeElement element) {
        Objects.requireNonNull(element, "Argument 'element'");
        try (var outputStream = new ByteArrayOutputStream(128)) {
            encodeToStream(element, outputStream);
            return outputStream.toByteArray();
        } catch (IOException cause) {
            throw new RuntimeException(cause);
        }
    }

    private OutputStream outputStream;

    public BencodeEncoder(@NotNull OutputStream outputStream) {
        Objects.requireNonNull(outputStream, "Argument 'outputStream'");
        this.outputStream = outputStream;
    }

    @Override
    public void close() throws IOException {
        outputStream.close();
        outputStream = null;
    }

    private byte[] buffer;

    private void write(byte[] bytes, int length) throws IOException {
        outputStream.write(bytes, 0, length);
    }
    private void write(byte[] bytes) throws IOException {
        outputStream.write(bytes);
    }
    private void write(byte value) throws IOException {
        outputStream.write(value);
    }

    private void write(char value) throws IOException {
        write((byte) value);
    }

    private void write(String value) throws IOException {
        write(value.getBytes(StandardCharsets.UTF_8));
    }

    private void write(long value) throws IOException {
        write(Long.toString(value));
    }
    private void write(int value) throws IOException {
        write(Integer.toString(value));
    }

    public void encode(@NotNull BencodeElement element) throws IOException {
        Objects.requireNonNull(element, "Argument 'element'");
        switch (element.getType()) {
            case INTEGER -> encode(element.asInteger());
            case STRING -> encode(element.asString());
            case LIST -> encode(element.asList());
            case MAP -> encode(element.asMap());
        }
    }

    public void encode(@NotNull BencodeInteger integer) throws IOException {
        Objects.requireNonNull(integer, "Argument 'integer'");
        write('i');
        write(integer.getValue());
        write('e');
    }

    public void encode(@NotNull BencodeString string) throws IOException {
        Objects.requireNonNull(string, "Argument 'string'");
        write(string.length());
        write(':');
        if (string.length() <= 512) {
            write(string.getBytes());
        } else {
            var buffer = this.buffer;
            if (buffer == null) {
                buffer = this.buffer = new byte[2048];
            }
            int offset = 0;
            while (true) {
                int count = string.copyTo(buffer, offset);
                if (count == 0) break;
                write(buffer, count);
                offset += count;
            }
        }
    }

    public void encode(@NotNull BencodeList list) throws IOException {
        Objects.requireNonNull(list, "Argument 'list'");
        write('l');
        for (var element : list) {
            if (element == null) continue;
            encode(element);
        }
        write('e');
    }

    public void encode(@NotNull BencodeMap map) throws IOException {
        Objects.requireNonNull(map, "Argument 'map'");
        write('d');
        for (var entry : map.entrySet()) {
            var key = entry.getKey();
            if (key == null) continue;
            var value = entry.getValue();
            if (value == null) continue;
            encode(key);
            encode(value);
        }
        write('e');
    }

}
