package net.azisaba.aziCave.dungeon;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

final class TickWork {
    private TickWork() {}

    static BooleanSupplier action(Runnable action) {
        return () -> {
            action.run();
            return true;
        };
    }

    static CompletableFuture<Void> run(JavaPlugin plugin, BooleanSupplier active, Collection<BooleanSupplier> steps) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ArrayDeque<BooleanSupplier> remaining = new ArrayDeque<>(steps);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    long deadline = System.nanoTime() + 3_000_000L;
                    do {
                        if (!active.getAsBoolean()) throw new CancellationException("Dungeon generation cancelled");
                        if (remaining.isEmpty()) {
                            cancel();
                            future.complete(null);
                            return;
                        }
                        // ponytail: the budget is checked between steps; split a step further if it dominates a tick.
                        if (remaining.peek().getAsBoolean()) remaining.remove();
                    } while (System.nanoTime() < deadline);
                } catch (RuntimeException ex) {
                    cancel();
                    future.completeExceptionally(ex);
                }
            }
        };
        task.runTaskTimer(plugin, 1L, 1L);
        future.whenComplete((ignored, ex) -> task.cancel());
        return future;
    }
}
