package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.util.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.channels.AsynchronousByteChannel;
import java.util.Objects;

public final class Peer implements AutoCloseable {

    private final @NotNull Protocol protocol;

    private boolean isPeerChoking = true;
    private boolean isPeerInterested = false;
    private boolean isClientChoking = true;
    private boolean isClientInterested = false;

    public Peer(@NotNull AsynchronousByteChannel channel) {
        Objects.requireNonNull(channel, "Argument 'channel'");
        protocol = new Protocol(channel);
        protocol.getConnectEvent().subscribe(this::onConnect);
        protocol.getCloseEvent().subscribe(this::onClose);
        protocol.getReceiveEvent().subscribe(this::onReceive);
    }

    public @NotNull Protocol getProtocol() {
        return protocol;
    }
    public @Nullable Identity getIdentity() {
        return protocol.getIdentity();
    }

    @Override
    public void close() {
        protocol.close();
    }

    private void onConnect(@NotNull Event.Subscription subscription, @NotNull Identity identity) {

    }

    private void onClose(@NotNull Event.Subscription subscription, @NotNull Throwable throwable) {

    }

    private void onReceive(@NotNull Event.Subscription subscription, @NotNull Message message) {
        if (message.getType() == MessageType.CHOKE) {
            isPeerChoking = true;
        }
        if (message.getType() == MessageType.UNCHOKE) {
            isPeerChoking = false;
        }
        if (message.getType() == MessageType.INTERESTED) {
            isPeerInterested = true;
        }
        if (message.getType() == MessageType.NOT_INTERESTED) {
            isPeerInterested = false;
        }
    }

}
