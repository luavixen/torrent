package dev.foxgirl.torrent.util;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

public final class Throwables {

    private Throwables() {
    }

    public static boolean canUnwrap(Throwable cause) {
        if (cause.getClass() == RuntimeException.class) {
            var message = cause.getMessage();
            return message == null || message.isEmpty();
        }
        return cause instanceof CompletionException || cause instanceof ExecutionException;
    }

    public static @NotNull Throwable unwrap(@NotNull Throwable throwable) {
        Objects.requireNonNull(throwable, "Argument 'throwable'");
        Throwable cause;
        while (canUnwrap(throwable) && (cause = throwable.getCause()) != null) {
            throwable = cause;
        }
        return throwable;
    }

    public static @NotNull String getMessage(@NotNull Throwable throwable) {
        Objects.requireNonNull(throwable, "Argument 'throwable'");
        String message;
        do {
            message = throwable.getMessage();
            throwable = throwable.getCause();
        } while (throwable != null && (message == null || message.isEmpty()));
        return message != null ? message : "";
    }

    public static boolean isExpected(@NotNull Throwable throwable) {
        Objects.requireNonNull(throwable, "Argument 'throwable'");
        return throwable instanceof IllegalStateException
            || throwable instanceof TimeoutException
            || throwable instanceof IOException;
    }

}
