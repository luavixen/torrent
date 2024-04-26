package dev.foxgirl.torrent.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Event<T> {

    public interface Listener<T> {
        void handle(@NotNull Subscription subscription, T value);
    }

    public interface Subscription {
        void unsubscribe();
    }

    private final Object lock = new Object();
    private final List<ListenerSubscription> subscriptions = new ArrayList<>();

    private final class ListenerSubscription implements Subscription {
        private final Listener<T> listener;

        private ListenerSubscription(Listener<T> listener) {
            this.listener = listener;
        }

        public void handle(T value) {
            listener.handle(this, value);
        }

        @Override
        public void unsubscribe() {
            synchronized (lock) {
                subscriptions.remove(this);
            }
        }
    }

    private final @Nullable Executor executor;

    public Event(@Nullable Executor executor) {
        this.executor = executor;
    }
    public Event() {
        this(DefaultExecutors.getDefaultExecutor());
    }

    public @NotNull Subscription subscribe(@NotNull Listener<T> listener) {
        Objects.requireNonNull(listener, "Argument 'listener'");
        var subscription = new ListenerSubscription(listener);
        synchronized (lock) {
            subscriptions.add(subscription);
        }
        return subscription;
    }

    public @NotNull Subscription subscribeOnce(@NotNull Listener<T> listener) {
        Objects.requireNonNull(listener, "Argument 'listener'");
        return subscribe(new Listener<T>() {
            private final AtomicBoolean isHandled = new AtomicBoolean();

            @Override
            public void handle(@NotNull Subscription subscription, T value) {
                if (isHandled.getAndSet(true)) {
                    return;
                }
                subscription.unsubscribe();
                listener.handle(subscription, value);
            }
        });
    }

    public void publish(T value) {
        List<ListenerSubscription> subscriptions;
        synchronized (lock) {
            subscriptions = new ArrayList<>(this.subscriptions);
        }
        if (executor != null) {
            for (var subscription : subscriptions) {
                executor.execute(() -> subscription.handle(value));
            }
        } else {
            for (var subscription : subscriptions) {
                subscription.handle(value);
            }
        }
    }

}
