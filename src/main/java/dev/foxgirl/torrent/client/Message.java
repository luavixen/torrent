package dev.foxgirl.torrent.client;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;

public abstract class Message {

    public abstract @NotNull MessageType getType();

    public abstract int getLength();

    public abstract @Nullable ByteBuffer getPayload();

    public @Nullable CompletableFuture<Void> writePayloadTo(@NotNull ByteBuffer buffer) {
        int length = getLength();
        if (length < 0) {
            throw new IllegalStateException("Default writePayloadTo with negative payload length");
        }
        var payload = getPayload();
        if (payload == null) {
            if (length != 0) {
                throw new IllegalStateException("Default writePayloadTo with null payload but nonzero payload length " + length);
            }
        } else {
            if (payload.remaining() != length) {
                throw new IllegalStateException("Default writePayloadTo with buffer of wrong length, expected " + length + ", actual " + payload.remaining());
            }
            buffer.put(payload);
        }
        return null;
    }

    @Override
    public @NotNull String toString() {
        return "Message{type=" + getType() + ", length=" + getLength() + "}";
    }

}
