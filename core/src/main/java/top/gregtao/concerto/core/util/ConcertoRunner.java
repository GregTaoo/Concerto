package top.gregtao.concerto.core.util;

import top.gregtao.concerto.core.Concerto;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ConcertoRunner {
    public static final Executor RUNNERS_POOL = Executors.newFixedThreadPool(16);

    public static void run(Runnable runnable) {
        CompletableFuture.runAsync(runnable, RUNNERS_POOL).exceptionally(e -> {
            Concerto.getLogger().error("Error occurred while executing asynchronous task", e);
            return null;
        });
    }

    public static void run(Runnable runnable, Runnable callback) {
        CompletableFuture.runAsync(runnable, RUNNERS_POOL).thenRunAsync(callback, RUNNERS_POOL).exceptionally(e -> {
            Concerto.getLogger().error("Error occurred while executing asynchronous task with callback", e);
            return null;
        });
    }
}
