package dev.foxgirl.torrent.client;

import dev.foxgirl.torrent.util.DefaultExecutors;
import dev.foxgirl.torrent.util.Hash;
import dev.foxgirl.torrent.util.IO;
import dev.foxgirl.torrent.util.Timeout;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class Protocol implements AutoCloseable {

    public interface Listener {
        void onReceive(@NotNull MessageImpl message) throws Exception;
        void onConnect(@NotNull Identity identity);
        void onClose(@NotNull Throwable cause);
    }

    private final @NotNull AsynchronousByteChannel channel;
    private final @NotNull Protocol.Listener listener;

    public Protocol(@NotNull AsynchronousByteChannel channel, @NotNull Protocol.Listener listener) {
        Objects.requireNonNull(channel, "Argument 'channel'");
        Objects.requireNonNull(listener, "Argument 'listener'");
        this.channel = channel;
        this.listener = listener;
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(Protocol.class);

    private static final byte[] PROTOCOL_STRING = "BitTorrent protocol".getBytes(StandardCharsets.ISO_8859_1);

    private static final Extensions SUPPORTED_EXTENSIONS = Extensions.getSupportedExtensions();

    private static final long READ_TIMEOUT_MS = 120 * 1000;
    private static final long WRITE_TIMEOUT_MS = 30 * 1000;
    private static final long OPERATION_TIMEOUT_MS = 15 * 1000;
    private static final long KEEPALIVE_INTERVAL_MS = 25 * 1000;

    private static final int READ_BUFFER_SIZE = 36 * 1024;
    private static final int WRITE_BUFFER_SIZE = 36 * 1024;

    // 0 = disconnected, 1 = connecting, 2 = connected
    private final AtomicInteger connectionState = new AtomicInteger();

    private final AtomicBoolean isClosed = new AtomicBoolean();

    private final Object lock = new Object();

    private Hash infoHash;
    private Identity identity;
    private Extensions extensions;

    private ReadHandler readHandler;
    private WriteHandler writeHandler;

    private final class ReadHandler implements CompletionHandler<Integer, Void> {
        private final ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER_SIZE);
        private final Timeout timeout = new Timeout(() -> close(new TimeoutException("Channel read timed out")));

        private void readFromChannel() {
            if (isClosed()) {
                close(new IllegalStateException("Channel closed"));
                return;
            }

            if (channel instanceof AsynchronousSocketChannel) {
                ((AsynchronousSocketChannel) channel).read(buffer, READ_TIMEOUT_MS, TimeUnit.MILLISECONDS, null, this);
            } else {
                channel.read(buffer, null, this);
            }

            timeout.start(READ_TIMEOUT_MS);
        }

        @Override
        public void completed(Integer result, Void attachment) {
            try {
                timeout.cancel();

                if (result < 0) {
                    close(new IllegalStateException("Channel read failed"));
                    return;
                }

                if (buffer.position() < 4) {
                    readFromChannel();
                    return;
                }

                int messageLength = buffer.getInt(0);
                if (messageLength < 0) {
                    throw new IllegalStateException("(Reading) Message length is negative: " + messageLength);
                }

                // Handle keep-alive message
                if (messageLength == 0) {
                    LOGGER.debug("Peer {} received keep-alive", getIdentity());
                    int remainingOffset = 4;
                    int remainingLength = buffer.position() - remainingOffset;
                    if (buffer.position() > 4) {
                        buffer.put(0, buffer, remainingOffset, remainingLength);
                        buffer.limit(buffer.capacity());
                        buffer.position(remainingLength);
                    }
                    readFromChannel();
                    return;
                }

                if (buffer.position() < 5) {
                    readFromChannel();
                    return;
                }

                int messageID = buffer.get(4) & 0xFF;
                var messageType = MessageType.valueOf(messageID);
                if (messageType == null) {
                    throw new IllegalStateException("(Reading) Message type not supported: " + String.format("0x%02X", messageID));
                }

                int messagePayloadLength = messageLength - 1;
                int messageTotalLength = messagePayloadLength + 5;
                if (messageTotalLength < 0) {
                    throw new IllegalStateException("(Reading) Message payload length is negative or too large: " + messagePayloadLength);
                }
                if (messageTotalLength > buffer.capacity()) {
                    throw new IllegalStateException("(Reading) Message total length exceeds buffer capacity: " + messageTotalLength);
                }

                if (messageTotalLength > buffer.position()) {
                    readFromChannel();
                    return;
                }

                LOGGER.debug("Peer {} received message {} with length {}", getIdentity(), messageType, messagePayloadLength);

                var messagePayload = buffer.asReadOnlyBuffer().limit(messageTotalLength).position(5);

                try {
                    listener.onReceive(new MessageImpl(messageType, messagePayload, messagePayloadLength));
                } catch (IllegalStateException cause) {
                    throw new IllegalStateException("(Reading)" + cause.getMessage(), cause);
                } catch (Throwable cause) {
                    throw new RuntimeException("(Reading) Failed to process message", cause);
                }

                int remainingOffset = messageTotalLength;
                int remainingLength = buffer.position() - remainingOffset;
                if (remainingLength > 0) {
                    buffer.put(0, buffer, remainingOffset, remainingLength);
                    buffer.limit(buffer.capacity());
                    buffer.position(remainingLength);
                } else {
                    buffer.clear();
                }

                readFromChannel();
            } catch (Throwable cause) {
                close(cause);
            }
        }

        @Override
        public void failed(Throwable cause, Void attachment) {
            close(cause);
        }

        private void closeHandler(Throwable cause) {
            timeout.cancel();
        }
    }

    private final class WriteHandler implements CompletionHandler<Integer, Void> {
        private final ByteBuffer buffer = ByteBuffer.allocateDirect(WRITE_BUFFER_SIZE);
        private final Timeout timeout = new Timeout(() -> close(new TimeoutException("Channel write timed out")));

        private final Queue<PendingMessage> pendingMessages = new ArrayDeque<>();
        private PendingMessage currentMessage = null;

        private final Runnable keepAliveTask = this::sendKeepAliveOnInterval;
        private ScheduledFuture<?> keepAliveFuture;

        private enum State {
            IDLE, WRITING_MESSAGE, WRITING_KEEPALIVE
        }
        private State state = State.IDLE;

        private static final class PendingMessage extends CompletableFuture<Void> {
            private final Message message;

            private PendingMessage(Message message) {
                Objects.requireNonNull(message, "Argument 'message'");
                this.message = message;
            }
        }

        private CompletableFuture<Void> send(Message message) {
            var pendingMessage = new PendingMessage(message);
            var shouldWriteNextMessage = false;
            synchronized (this) {
                pendingMessages.offer(pendingMessage);
                if (state == State.IDLE) shouldWriteNextMessage = true;
            }
            if (shouldWriteNextMessage) {
                writeNextMessage();
            }
            return pendingMessage;
        }

        private void writeNextMessage() {
            PendingMessage pendingMessage;
            synchronized (this) {
                pendingMessage = pendingMessages.poll();
                if (pendingMessage == null) {
                    state = State.IDLE;
                    return;
                } else {
                    state = State.WRITING_MESSAGE;
                }
            }
            writeMessage(pendingMessage);
        }

        private void writeMessage(PendingMessage pendingMessage) {
            try {
                synchronized (this) {
                    currentMessage = pendingMessage;
                }

                var messageType = pendingMessage.message.getType();
                if (messageType == null) {
                    throw new IllegalStateException("(Writing) Message type is null");
                }

                var messageLength = pendingMessage.message.getLength();
                if (messageLength < 0) {
                    throw new IllegalStateException("(Writing) Message length is negative: " + messageLength);
                }
                if (messageLength > buffer.capacity() - 5) {
                    throw new IllegalStateException("(Writing) Message length exceeds buffer capacity: " + messageLength);
                }

                LOGGER.debug("Peer {} sending message {} with length {}", getIdentity(), messageType, messageLength);

                buffer.clear();
                buffer.putInt(messageLength + 1);
                buffer.put(messageType.getID());

                CompletableFuture<Void> future;
                try {
                    future = pendingMessage.message.writePayloadTo(buffer);
                } catch (Throwable cause) {
                    throw new RuntimeException("Failed to write message", cause);
                }
                if (future != null) {
                    future = Timeout.timeoutCompletableFuture(OPERATION_TIMEOUT_MS, future);
                    future.whenCompleteAsync((result, cause) -> {
                        if (cause != null) {
                            pendingMessage.completeExceptionally(cause);
                        } else {
                            buffer.flip();
                            writeToChannel();
                        }
                    }, DefaultExecutors.getDefaultExecutor());
                } else {
                    buffer.flip();
                    writeToChannel();
                }
            } catch (Throwable cause) {
                pendingMessage.completeExceptionally(cause);
                close(cause);
            }
        }

        private void sendKeepAliveOnIntervalAfterDelay() {
            if (isClosed()) {
                return;
            }
            keepAliveFuture = DefaultExecutors.getScheduledExecutor().schedule(keepAliveTask, KEEPALIVE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }

        private void sendKeepAliveOnInterval() {
            if (isClosed()) {
                return;
            }
            sendKeepAlive();
            sendKeepAliveOnIntervalAfterDelay();
        }

        private void sendKeepAlive() {
            synchronized (this) {
                if (state != State.IDLE) {
                    return;
                }
                state = State.WRITING_KEEPALIVE;
            }
            writeKeepAlive();
        }

        private void writeKeepAlive() {
            try {
                LOGGER.debug("Peer {} sending keep-alive", getIdentity());
                buffer.clear();
                buffer.putInt(0);
                buffer.flip();
                writeToChannel();
            } catch (Throwable cause) {
                close(cause);
            }
        }

        private void writeToChannel() {
            if (isClosed()) {
                close(new IllegalStateException("Channel closed"));
                return;
            }

            if (channel instanceof AsynchronousSocketChannel) {
                ((AsynchronousSocketChannel) channel).write(buffer, WRITE_TIMEOUT_MS, TimeUnit.MILLISECONDS, null, this);
            } else {
                channel.write(buffer, null, this);
            }

            timeout.start(WRITE_TIMEOUT_MS);
        }

        @Override
        public void completed(Integer result, Void attachment) {
            try {
                timeout.cancel();

                if (result < 0) {
                    close(new IllegalStateException("Channel write failed"));
                    return;
                }

                if (buffer.hasRemaining()) {
                    writeToChannel();
                    return;
                }

                if (state != State.WRITING_KEEPALIVE) {
                    PendingMessage pendingMessage;
                    synchronized (this) {
                        pendingMessage = currentMessage;
                        currentMessage = null;
                    }
                    if (pendingMessage == null) {
                        throw new IllegalStateException("No current message");
                    }
                    pendingMessage.complete(null);
                }

                writeNextMessage();
            } catch (Throwable cause) {
                close(cause);
            }
        }

        @Override
        public void failed(Throwable cause, Void attachment) {
            close(cause);
        }

        private void closeHandler(Throwable cause) {
            timeout.cancel();

            ScheduledFuture<?> keepAliveFuture;
            synchronized (this) {
                keepAliveFuture = this.keepAliveFuture;
            }
            if (keepAliveFuture != null) {
                keepAliveFuture.cancel(true);
            }

            PendingMessage currentMessage;
            PendingMessage[] pendingMessagesArray;
            synchronized (this) {
                currentMessage = this.currentMessage;
                pendingMessagesArray = pendingMessages.toArray(new PendingMessage[0]);
                pendingMessages.clear();
            }
            if (currentMessage != null) {
                currentMessage.completeExceptionally(cause);
            }
            for (var pendingMessage : pendingMessagesArray) {
                pendingMessage.completeExceptionally(cause);
            }
        }
    }

    public boolean isConnected() {
        return connectionState.get() == 2;
    }

    private void setConnectionStateDisconnected() {
        connectionState.set(0);
    }
    private void setConnectionStateConnected() {
        connectionState.set(2);
    }

    private boolean trySetConnectionStateConnecting() {
        return connectionState.getAndUpdate(value -> value == 0 ? 1 : value) != 0;
    }

    public boolean isClosed() {
        if (isClosed.get()) return true;
        if (!channel.isOpen()) {
            close();
            return true;
        }
        return false;
    }

    private void assertNotClosed() {
        if (isClosed()) {
            throw new IllegalStateException("Peer is closed");
        }
    }

    private void close(@Nullable Throwable cause) {
        if (isClosed.getAndSet(true)) {
            return;
        }

        setConnectionStateDisconnected();

        if (cause == null) {
            cause = new IllegalStateException("Peer closed");
        }

        var message = cause.getMessage();
        if (message == null) {
            message = cause.getClass().getName();
        }

        LOGGER.info("Peer {} closed: {}", getIdentity(), message);

        if (!(cause instanceof IllegalStateException) && !(cause instanceof TimeoutException)) {
            LOGGER.debug("Peer {} closed with unexpected exception", getIdentity(), cause);
        }

        // Close read/write handlers

        ReadHandler readHandler;
        WriteHandler writeHandler;
        synchronized (lock) {
            readHandler = this.readHandler;
            writeHandler = this.writeHandler;
            this.readHandler = null;
            this.writeHandler = null;
        }
        if (readHandler != null) {
            try {
                readHandler.closeHandler(cause);
            } catch (Throwable readHandlerCause) {
                cause.addSuppressed(readHandlerCause);
            }
        }
        if (writeHandler != null) {
            try {
                writeHandler.closeHandler(cause);
            } catch (Throwable writeHandlerCause) {
                cause.addSuppressed(writeHandlerCause);
            }
        }

        // Close channel

        try {
            channel.close();
        } catch (Throwable channelCause) {
            cause.addSuppressed(channelCause);
        }

        // Publish close event

        try {
            listener.onClose(cause);
        } catch (Throwable ignored) {}
    }

    @Override
    public void close() {
        close(null);
    }

    public @NotNull CompletableFuture<Void> send(@NotNull Message message) {
        Objects.requireNonNull(message, "Argument 'message'");
        assertNotClosed();
        WriteHandler writeHandler;
        synchronized (lock) {
            writeHandler = this.writeHandler;
        }
        if (writeHandler == null) {
            throw new IllegalStateException("Peer not connected/established");
        }
        return writeHandler.send(message);
    }

    private static final class EstablishIncomingHandshake {
        private final @NotNull Extensions peerExtensions;
        private final @NotNull Hash peerInfoHash;
        private final @NotNull Identity peerIdentity;

        private EstablishIncomingHandshake(
                @NotNull Extensions peerExtensions,
                @NotNull Hash peerInfoHash,
                @NotNull Identity peerIdentity
        ) {
            this.peerExtensions = peerExtensions;
            this.peerInfoHash = peerInfoHash;
            this.peerIdentity = peerIdentity;
        }
    }

    private interface EstablishCallback {
        @NotNull EstablishIncomingHandshake establish(@NotNull ByteBuffer buffer, @NotNull Timeout timeout) throws Throwable;
    }

    private @NotNull CompletableFuture<@NotNull Identity> establish(@NotNull InetSocketAddress peerAddress, @NotNull EstablishCallback callback) {
        if (trySetConnectionStateConnecting()) {
            throw new IllegalStateException("Peer already connected");
        }
        return CompletableFuture.supplyAsync(() -> {
            assertNotClosed();

            var buffer = ByteBuffer.allocate(68);
            var timeout = new Timeout(() -> close(new TimeoutException("Handshake timed out")));

            try {
                var peerHandshake = callback.establish(buffer, timeout);

                var readHandler = new ReadHandler();
                var writeHandler = new WriteHandler();

                synchronized (lock) {
                    this.infoHash = peerHandshake.peerInfoHash;
                    this.identity = peerHandshake.peerIdentity;
                    this.extensions = peerHandshake.peerExtensions;
                    this.readHandler = readHandler;
                    this.writeHandler = writeHandler;
                }

                assertNotClosed();

                readHandler.readFromChannel();
                writeHandler.sendKeepAliveOnIntervalAfterDelay();

                setConnectionStateConnected();

                LOGGER.debug("Peer {} socket {} connected", peerHandshake.peerIdentity, peerAddress);
                LOGGER.info("Peer {} connected", peerHandshake.peerIdentity);

                listener.onConnect(peerHandshake.peerIdentity);

                return peerHandshake.peerIdentity;
            } catch (Throwable cause) {
                LOGGER.info("Peer socket {} connection failed", peerAddress, cause);
                close(cause);
                throw cause instanceof RuntimeException ? (RuntimeException) cause : new CompletionException(cause);
            } finally {
                timeout.cancel();
            }
        }, DefaultExecutors.getIOExecutor());
    }

    private EstablishIncomingHandshake establishReadIncomingHandshake(
            @NotNull InetSocketAddress peerAddress,
            @NotNull ByteBuffer buffer,
            @NotNull Timeout timeout,
            @Nullable Hash infoHash
    ) throws IOException {
        // Read incoming handshake

        assertNotClosed();

        LOGGER.debug("Peer socket {} connecting, receiving response handshake", peerAddress);

        timeout.start(READ_TIMEOUT_MS);
        IO.readAll(Channels.newInputStream(channel), buffer.array(), 0, 68);
        timeout.cancel();

        var peerPstrLen = buffer.get();
        if (peerPstrLen != PROTOCOL_STRING.length) {
            throw new IllegalStateException(String.format(
                "Handshake protocol string length mismatch, expected %d, actual %d",
                PROTOCOL_STRING.length, peerPstrLen
            ));
        }
        var peerPstr = IO.getArray(buffer, PROTOCOL_STRING.length);
        if (!Arrays.equals(peerPstr, PROTOCOL_STRING)) {
            throw new IllegalStateException(String.format(
                "Handshake protocol string mismatch, expected \"%s\", actual \"%s\"",
                new String(PROTOCOL_STRING, StandardCharsets.ISO_8859_1),
                new String(peerPstr, StandardCharsets.ISO_8859_1)
            ));
        }

        var peerReservedBytes = IO.getArray(buffer, 8);
        var peerInfoHashBytes = IO.getArray(buffer, 20);
        var peerIdentityBytes = IO.getArray(buffer, 20);

        // Process incoming handshake and setup protocol

        var peerExtensions = new Extensions(peerReservedBytes);
        var peerInfoHash = Hash.of(peerInfoHashBytes);
        var peerIdentity = new Identity(peerIdentityBytes, peerAddress);

        if (infoHash != null && !Objects.equals(infoHash, peerInfoHash)) {
            throw new IllegalStateException("Handshake infohash mismatch, expected " + infoHash + ", actual " + peerInfoHash);
        }

        return new EstablishIncomingHandshake(peerExtensions, peerInfoHash, peerIdentity);
    }

    private void establishWriteOutgoingHandshake(
            @NotNull InetSocketAddress peerAddress,
            @NotNull ByteBuffer buffer,
            @NotNull Timeout timeout,
            @NotNull Hash infoHash,
            @NotNull Identity clientIdentity
    ) throws IOException {
        // Write outgoing handshake

        assertNotClosed();

        LOGGER.debug("Peer socket {} connecting, sending request handshake", peerAddress);

        /* clientPstrLen       */ buffer.put((byte) PROTOCOL_STRING.length);
        /* clientPstr          */ buffer.put(PROTOCOL_STRING);
        /* clientReservedBytes */ buffer.put(SUPPORTED_EXTENSIONS.getBits());
        /* clientInfoHashBytes */ buffer.put(infoHash.getBytes());
        /* clientIdentityBytes */ buffer.put(clientIdentity.getID());
        buffer.flip();

        timeout.start(WRITE_TIMEOUT_MS);
        Channels.newOutputStream(channel).write(buffer.array(), 0, buffer.limit());
        timeout.cancel();

        buffer.clear();
    }

    public @NotNull CompletableFuture<@NotNull Identity> establishOutgoing(
            @NotNull Hash infoHash,
            @NotNull Identity clientIdentity,
            @NotNull InetSocketAddress peerAddress
    ) {
        Objects.requireNonNull(infoHash, "Argument 'infoHash'");
        Objects.requireNonNull(clientIdentity, "Argument 'clientIdentity'");
        Objects.requireNonNull(peerAddress, "Argument 'peerAddress'");
        return establish(peerAddress, (buffer, timeout) -> {
            establishWriteOutgoingHandshake(peerAddress, buffer, timeout, infoHash, clientIdentity);
            return establishReadIncomingHandshake(peerAddress, buffer, timeout, infoHash);
        });
    }
    public @NotNull CompletableFuture<@NotNull Identity> establishIncoming(
            @NotNull Identity clientIdentity,
            @NotNull InetSocketAddress peerAddress
    ) {
        Objects.requireNonNull(clientIdentity, "Argument 'clientIdentity'");
        Objects.requireNonNull(peerAddress, "Argument 'peerAddress'");
        return establish(peerAddress, (buffer, timeout) -> {
            var peerHandshake = establishReadIncomingHandshake(peerAddress, buffer, timeout, infoHash);
            establishWriteOutgoingHandshake(peerAddress, buffer, timeout, peerHandshake.peerInfoHash, clientIdentity);
            return peerHandshake;
        });
    }

    public @Nullable Hash getInfoHash() {
        synchronized (lock) {
            return infoHash;
        }
    }

    public @Nullable Identity getIdentity() {
        synchronized (lock) {
            return identity;
        }
    }
    public @Nullable Extensions getExtensions() {
        synchronized (lock) {
            return extensions;
        }
    }

    @Override
    public @NotNull String toString() {
        synchronized (lock) {
            return String.format(
                "Protocol{infoHash=%s, identity=%s, extensions=%s, isClosed=%s}",
                infoHash, identity, extensions, isClosed()
            );
        }
    }

}
