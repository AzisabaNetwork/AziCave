package net.azisaba.aziCave.achievement;

import net.azisaba.aziCave.AziCave;
import net.azisaba.azisabaachievements.api.AzisabaAchievementsProvider;
import net.azisaba.azisabaachievements.api.Key;
import net.azisaba.azisabaachievements.api.achievement.AchievementManager;
import net.azisaba.azisabaachievements.api.achievement.ProgressProvider;
import net.azisaba.azisabaachievements.spigot.event.ProgressUpdatedEvent;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registers {@link StatisticsProgressProvider} and asks AzisabaAchievements to resync players whose statistics
 * changed. Updates are batched so frequent events such as mob kills do not trigger a sync each time.
 * Loaded reflectively so AziCave still runs without AzisabaAchievements installed.
 */
public final class AzisabaAchievementsBridge implements AchievementBridge {
    private static final long FLUSH_INTERVAL_TICKS = 100L;
    private static final Key BOSS_DEFEATED = Key.key("azisaba:azicave/boss-defeated");
    private static final Key BOSS_KILLS = Key.key("azisaba:azicave/boss-kills");

    private final AziCave plugin;
    private final StatisticsProgressProvider provider;
    private final Set<UUID> pendingUpdates = ConcurrentHashMap.newKeySet();
    private final BukkitTask flushTask;

    public AzisabaAchievementsBridge(AziCave plugin) {
        this.plugin = plugin;
        this.provider = new StatisticsProgressProvider(plugin.statisticsService());
        Bukkit.getServicesManager().register(ProgressProvider.class, provider, plugin, ServicePriority.Normal);
        plugin.statisticsService().setPlayerUpdateListener(pendingUpdates::add);
        this.flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flush, FLUSH_INTERVAL_TICKS, FLUSH_INTERVAL_TICKS);
    }

    @Override
    public String describeAvailability() {
        return "AzisabaAchievements";
    }

    @Override
    public void recordBossDefeat(Collection<UUID> playerIds) {
        AchievementManager manager = AzisabaAchievementsProvider.get().getAchievementManager();
        for (UUID playerId : playerIds) {
            progress(manager, playerId, BOSS_DEFEATED);
            progress(manager, playerId, BOSS_KILLS);
        }
    }

    @Override
    public void shutdown() {
        flushTask.cancel();
        plugin.statisticsService().setPlayerUpdateListener(playerId -> {
        });
        pendingUpdates.clear();
        Bukkit.getServicesManager().unregister(ProgressProvider.class, provider);
    }

    private void progress(AchievementManager manager, UUID playerId, Key key) {
        manager.progressAchievement(playerId, key, 1L).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().warning("Failed to progress achievement " + key + " for " + playerId + ": "
                        + error.getMessage());
            }
        });
    }

    private void flush() {
        if (pendingUpdates.isEmpty()) {
            return;
        }
        List<UUID> playerIds = new ArrayList<>(pendingUpdates);
        pendingUpdates.removeAll(playerIds);
        for (UUID playerId : playerIds) {
            new ProgressUpdatedEvent(playerId).callEvent();
        }
    }
}
