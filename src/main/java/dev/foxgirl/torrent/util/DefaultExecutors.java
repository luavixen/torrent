package dev.foxgirl.torrent.util;

import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ScheduledExecutorService;

public final class DefaultExecutors {

    private DefaultExecutors() {
    }

    private static final ExecutorService DEFAULT_EXECUTOR_SERVICE;
    private static final ExecutorService IO_EXECUTOR_SERVICE;
    private static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE;

    static {
        /*
        ExecutorService defaultExecutorService;
        try {
            defaultExecutorService = (ExecutorService) Executors.class.getMethod("newVirtualThreadPerTaskExecutor").invoke(null);
        } catch (Exception ignored) {
            defaultExecutorService = Executors.newCachedThreadPool();
        }
        DEFAULT_EXECUTOR_SERVICE = defaultExecutorService;
        */
        DEFAULT_EXECUTOR_SERVICE = ForkJoinPool.commonPool();
        IO_EXECUTOR_SERVICE = Executors.newCachedThreadPool();
        SCHEDULED_EXECUTOR_SERVICE = Executors.newSingleThreadScheduledExecutor();
    }

    public static @NotNull ExecutorService getDefaultExecutor() {
        return DEFAULT_EXECUTOR_SERVICE;
    }

    public static @NotNull ExecutorService getIOExecutor() {
        return IO_EXECUTOR_SERVICE;
    }

    public static @NotNull ScheduledExecutorService getScheduledExecutor() {
        return SCHEDULED_EXECUTOR_SERVICE;
    }

    public static void shutdown() {
        DEFAULT_EXECUTOR_SERVICE.shutdown();
        IO_EXECUTOR_SERVICE.shutdown();
        SCHEDULED_EXECUTOR_SERVICE.shutdown();
    }

}
