package net.azisaba.aziCave.achievement;

import java.util.Collection;
import java.util.UUID;

public interface AchievementBridge {
    String describeAvailability();

    void recordBossDefeat(Collection<UUID> playerIds);

    void shutdown();
}
