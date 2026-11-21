package net.azisaba.aziCave.achievement;

import net.azisaba.aziCave.statistics.PlayerStatistics;
import net.azisaba.aziCave.statistics.StatisticsService;
import net.azisaba.azisabaachievements.api.Key;
import net.azisaba.azisabaachievements.api.achievement.ProgressProvider;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link ProgressProvider} backed by the AziCave statistics database.
 * Registering it hides AzisabaAchievements' built-in provider, so keys not derived from statistics (such as boss
 * defeats) are read from and written to the next registered provider instead.
 * AzisabaAchievements calls it off the main thread, so blocking on the statistics thread is fine here.
 */
public final class StatisticsProgressProvider implements ProgressProvider {
    private static final long TIMEOUT_SECONDS = 10L;

    private final StatisticsService statisticsService;
    private final Map<AchievementProgress, Key> keys = new EnumMap<>(AchievementProgress.class);
    private final Set<Key> statisticKeys;

    public StatisticsProgressProvider(StatisticsService statisticsService) {
        this.statisticsService = Objects.requireNonNull(statisticsService, "statisticsService");
        for (AchievementProgress progress : AchievementProgress.values()) {
            keys.put(progress, Key.key(progress.key()));
        }
        this.statisticKeys = Set.copyOf(keys.values());
    }

    @Override
    public @NotNull Map<Key, Long> getProgress(@NotNull UUID playerId) {
        Map<Key, Long> result = new HashMap<>();
        ProgressProvider fallback = fallback();
        if (fallback != null) {
            result.putAll(fallback.getProgress(playerId));
        }
        Optional<PlayerStatistics> statistics = await(playerId);
        if (statistics.isEmpty()) {
            if (!statisticsService.isAvailable()) {
                // Throwing keeps the cached progress instead of resetting it to 0 while the database is down.
                throw new IllegalStateException("AziCave statistics database is unavailable");
            }
            return result;
        }
        for (Map.Entry<AchievementProgress, Key> entry : keys.entrySet()) {
            result.put(entry.getValue(), entry.getKey().valueOf(statistics.get()));
        }
        return result;
    }

    @Override
    public void addProgress(@NotNull UUID playerId, @NotNull Key key, long amount) {
        requireFallback(key).addProgress(playerId, key, amount);
    }

    @Override
    public void setProgress(@NotNull UUID playerId, @NotNull Key key, long count) {
        requireFallback(key).setProgress(playerId, key, count);
    }

    private ProgressProvider requireFallback(Key key) {
        if (statisticKeys.contains(key)) {
            throw new UnsupportedOperationException(key + " is derived from AziCave statistics and is read-only");
        }
        ProgressProvider fallback = fallback();
        if (fallback == null) {
            throw new UnsupportedOperationException("No ProgressProvider is available to store " + key);
        }
        return fallback;
    }

    private ProgressProvider fallback() {
        // Registrations are ordered by priority, so this is the provider that would be used without AziCave.
        for (RegisteredServiceProvider<ProgressProvider> registration
                : Bukkit.getServicesManager().getRegistrations(ProgressProvider.class)) {
            if (registration.getProvider() != this) {
                return registration.getProvider();
            }
        }
        return null;
    }

    private Optional<PlayerStatistics> await(UUID playerId) {
        try {
            return statisticsService.playerStatistics(playerId).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while loading AziCave statistics", ex);
        } catch (ExecutionException ex) {
            throw new IllegalStateException("Failed to load AziCave statistics", ex.getCause());
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timed out loading AziCave statistics", ex);
        }
    }
}
