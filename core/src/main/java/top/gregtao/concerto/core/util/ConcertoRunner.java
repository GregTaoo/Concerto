package top.gregtao.concerto.core.util;

import top.gregtao.concerto.core.Concerto;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcertoRunner {

    /**
     * Daemon threads: Minecraft's quit path does not always reach
     * {@code System.exit}, so a non-daemon worker would pin the JVM forever.
     */
    public static final ExecutorService RUNNERS_POOL =
            Executors.newFixedThreadPool(16, daemonThreadFactory("Concerto-Runner"));

    /** Named, daemon {@link ThreadFactory} for Concerto's background pools. */
    public static ThreadFactory daemonThreadFactory(String baseName) {
        AtomicInteger counter = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, baseName + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Belt and suspenders for the quit path: the workers are daemon threads
     * already, but stop them explicitly as well. After this, further
     * {@link #run} calls are dropped with a warning instead of throwing.
     */
    public static void shutdown() {
        RUNNERS_POOL.shutdownNow();
    }

    public static void run(Runnable runnable) {
        try {
            CompletableFuture.runAsync(runnable, RUNNERS_POOL).exceptionally(e -> {
                Concerto.getLogger().error("Error occurred while executing asynchronous task", e);
                return null;
            });
        } catch (RejectedExecutionException e) {
            Concerto.getLogger().warn("Dropped asynchronous task: runner pool is shut down");
        }
    }

    public static void run(Runnable runnable, Runnable callback) {
        try {
            CompletableFuture.runAsync(runnable, RUNNERS_POOL).thenRunAsync(callback, RUNNERS_POOL).exceptionally(e -> {
                Concerto.getLogger().error("Error occurred while executing asynchronous task with callback", e);
                return null;
            });
        } catch (RejectedExecutionException e) {
            Concerto.getLogger().warn("Dropped asynchronous task: runner pool is shut down");
        }
    }
}
