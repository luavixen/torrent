package dev.foxgirl.torrent.util;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;

public final class Timeout {

    private static final class CompletableFutureTimeout<T> implements Runnable, BiConsumer<T, Throwable> {
        private final Timeout timeout;
        private final CompletableFuture<T> future;

        private CompletableFutureTimeout(CompletableFuture<T> future) {
            this.timeout = new Timeout(this);
            this.future = future.whenComplete(this);
        }

        private CompletableFuture<T> start(long ms) {
            timeout.start(ms);
            return future;
        }

        // Run by timeout when the timeout is reached
        @Override
        public void run() {
            future.completeExceptionally(new TimeoutException());
        }

        // Run by future on completion
        @Override
        public void accept(T result, Throwable cause) {
            timeout.cancel();
        }
    }

    public static <T> @NotNull CompletableFuture<T> timeoutCompletableFuture(long ms, CompletableFuture<T> future) {
        Objects.requireNonNull(future, "Argument 'future'");
        return new CompletableFutureTimeout<>(future).start(ms);
    }

    private final Runnable action;
    private ScheduledFuture<?> future;

    public Timeout(@NotNull Runnable action) {
        Objects.requireNonNull(action, "Argument 'action'");
        this.action = action;
    }

    public synchronized void cancel() {
        if (future != null) {
            future.cancel(true);
        }
        future = null;
    }

    public synchronized void start(long ms) {
        cancel();
        if (ms > 0) {
            future = DefaultExecutors.getScheduledExecutor().schedule(action, ms, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public @NotNull String toString() {
        return "Timeout{action=" + action + "}";
    }

}
