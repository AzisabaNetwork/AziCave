package net.azisaba.aziCave.achievement;

import java.util.Collection;
import java.util.UUID;

public final class MissingAchievementBridge implements AchievementBridge {
    private final String reason;

    public MissingAchievementBridge(String reason) {
        this.reason = reason;
    }

    @Override
    public String describeAvailability() {
        return "unavailable(" + reason + ")";
    }

    @Override
    public void recordBossDefeat(Collection<UUID> playerIds) {
    }

    @Override
    public void shutdown() {
    }
}
