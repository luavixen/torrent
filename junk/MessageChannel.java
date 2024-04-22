package dev.foxgirl.torrent.client;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousByteChannel;
import java.nio.channels.Channel;
import java.nio.channels.CompletionHandler;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public abstract class MessageChannel implements Channel {

    private final AsynchronousByteChannel channel;

    public MessageChannel(@NotNull AsynchronousByteChannel channel) {
        Objects.requireNonNull(channel, "Argument 'channel'");
        this.channel = channel;
    }

    private static abstract sealed class SendRequest permits CompletableFutureSendRequest, CompletionHandlerSendRequest {
        private final Message message;

        private SendRequest(Message message) {
            this.message = message;
        }

        abstract void complete();
        abstract void completeExceptionally(Throwable cause);
    }

    private static final class CompletableFutureSendRequest extends SendRequest {
        private final CompletableFuture<Void> future;

        private CompletableFutureSendRequest(Message message, CompletableFuture<Void> future) {
            super(message);
            this.future = future;
        }

        @Override
        void complete() { future.complete(null); }
        @Override
        void completeExceptionally(Throwable cause) { future.completeExceptionally(cause); }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static final class CompletionHandlerSendRequest extends SendRequest {
        private final Object attachment;
        private final CompletionHandler handler;

        private CompletionHandlerSendRequest(Message message, Object attachment, CompletionHandler<Void, ?> handler) {
            super(message);
            this.attachment = attachment;
            this.handler = handler;
        }

        @Override
        void complete() { handler.completed(null, attachment); }
        @Override
        void completeExceptionally(Throwable cause) { handler.failed(cause, attachment); }
    }

    private final Queue<SendRequest> sendQueue = new ArrayDeque<>();
    private final ByteBuffer sendBuffer = ByteBuffer.allocate(32768);

    private boolean isSending = false;

    private void send() {
        var request = sendQueue.poll();
        if (request == null) {
            return;
        }
        sendBuffer.clear();
        request.message.writeTo(sendBuffer);
        sendBuffer.flip();
        channel.write(sendBuffer, null, new CompletionHandler<Integer, Void>() {
            @Override
            public void completed(Integer result, Void attachment) {
                if (sendBuffer.hasRemaining()) {
                    channel.write(sendBuffer, null, this);
                    return;
                }
                request.complete();
                isSending = false;
                send();
            }
            @Override
            public void failed(Throwable exc, Void attachment) {
                request.completeExceptionally(exc);
                isSending = false;
                send();
            }
        });
    }

    public @NotNull Future<@NotNull Void> write(@NotNull Message message) {
        Objects.requireNonNull(message, "Argument 'message'");
        var future = new CompletableFuture<Void>();
        sendQueue.add(new CompletableFutureSendRequest(message, future));
        return future;
    }

    public <A> void write(@NotNull Message message, A attachment, @NotNull CompletionHandler<@NotNull Void, ? super A> handler) {
        Objects.requireNonNull(message, "Argument 'message'");
        Objects.requireNonNull(attachment, "Argument 'attachment'");
        Objects.requireNonNull(handler, "Argument 'handler'");
        sendQueue.add(new CompletionHandlerSendRequest(message, attachment, handler));
    }



}
