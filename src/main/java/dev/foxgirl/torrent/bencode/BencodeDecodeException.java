package dev.foxgirl.torrent.bencode;

import java.io.IOException;
import java.io.Serial;

public class BencodeDecodeException extends IOException {

    @Serial
    private static final long serialVersionUID = -7444516590442325786L;

    public BencodeDecodeException() {
    }

    public BencodeDecodeException(String message) {
        super(message);
    }

    public BencodeDecodeException(String message, Throwable cause) {
        super(message, cause);
    }

    public BencodeDecodeException(Throwable cause) {
        super(cause);
    }

}
