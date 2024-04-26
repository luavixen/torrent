package dev.foxgirl.torrent.client;

import org.jetbrains.annotations.Nullable;

public enum MessageType {

    CHOKE(0),
    UNCHOKE(1),
    INTERESTED(2),
    NOT_INTERESTED(3),
    HAVE(4),
    BITFIELD(5),
    REQUEST(6),
    PIECE(7),
    CANCEL(8),
    PORT(9),
    HAVE_ALL(14),
    HAVE_NONE(15),
    SUGGEST_PIECE(13),
    REJECT_REQUEST(16),
    ALLOWED_FAST(17),
    EXTENDED(20);

    private final byte id;

    MessageType(int id) {
        this.id = (byte) id;
    }

    public byte getID() {
        return id;
    }

    public static @Nullable MessageType valueOf(int id) {
        for (MessageType type : values()) {
            if ((type.getID() & 0xFF) == id) {
                return type;
            }
        }
        return null;
    }

}
