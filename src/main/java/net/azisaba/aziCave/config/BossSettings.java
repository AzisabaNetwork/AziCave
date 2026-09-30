package net.azisaba.aziCave.config;

import java.util.List;

public record BossSettings(
        List<BossBattleSettings> battles,
        double reviveRadius,
        int reviveHoldTicks,
        int portalCooldownSeconds
) {
}
