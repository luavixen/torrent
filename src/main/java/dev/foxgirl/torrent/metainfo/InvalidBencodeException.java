package dev.foxgirl.torrent.metainfo;

import java.io.Serial;

public class InvalidBencodeException extends Exception {

    @Serial
    private static final long serialVersionUID = -1255063724379892605L;

    public InvalidBencodeException() {
    }

    public InvalidBencodeException(String message) {
        super(message);
    }

    public InvalidBencodeException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidBencodeException(Throwable cause) {
        super(cause);
    }

}
