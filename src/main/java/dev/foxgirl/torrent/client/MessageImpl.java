package dev.foxgirl.torrent.client;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class MessageImpl extends Message {

    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0).asReadOnlyBuffer();

    private final MessageType type;
    private final ByteBuffer payload;
    private final int length;

    public MessageImpl(@NotNull MessageType type, @NotNull ByteBuffer payload, int length) {
        Objects.requireNonNull(type, "Argument 'type'");
        Objects.requireNonNull(payload, "Argument 'payload'");
        if (length < 0) {
            throw new IllegalArgumentException("Length is negative");
        }
        if (length != payload.remaining()) {
            throw new IllegalArgumentException("Length is not equal to payload remaining");
        }
        this.type = type;
        this.payload = payload;
        this.length = length;
    }

    public MessageImpl(@NotNull MessageType type, @NotNull ByteBuffer payload) {
        this(type, payload, payload.remaining());
    }

    public MessageImpl(@NotNull MessageType type) {
        this(type, EMPTY);
    }

    @Override
    public @NotNull MessageType getType() {
        return type;
    }

    @Override
    public int getLength() {
        return length;
    }

    @Override
    public @NotNull ByteBuffer getPayload() {
        return payload;
    }

}
