package dev.foxgirl.torrent.client;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

public final class Message {

    private final MessageType type;
    private final ByteBuffer payload;

    public Message(@NotNull MessageType type, @NotNull ByteBuffer payload) {
        Objects.requireNonNull(type, "Argument 'type'");
        Objects.requireNonNull(payload, "Argument 'payload'");
        this.type = type;
        this.payload = payload;
    }

    public @NotNull MessageType getType() {
        return type;
    }

    public @NotNull ByteBuffer getPayload() {
        return payload;
    }

    public void writeTo(@NotNull ByteBuffer buffer) {
        Objects.requireNonNull(buffer, "Argument 'buffer'");
        buffer.putInt(1 + payload.limit());
        buffer.put(type.getID());
        buffer.put(payload);
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", length=" + payload.limit() + "}";
    }

}
