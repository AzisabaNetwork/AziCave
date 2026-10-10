package net.azisaba.aziCave.config;

import java.util.List;

public record MobProfileSettings(
        int weight,
        int power,
        double maxHealth,
        double movementSpeed,
        double attackDamage,
        int maxAliveCount,
        MaxAliveCountScaling maxAliveCountScaling,
        MobAiSettings ai,
        List<MobDropEntrySettings> drops
) {
    public int scaledMaxAliveCount(MobSpawnSettings.SpawnPressure spawnPressure) {
        if (maxAliveCount < 0) {
            return maxAliveCount;
        }
        double bonus = spawnPressure.quotaLevel() * maxAliveCountScaling.quotaBonus()
                + spawnPressure.noDeathLevel() * maxAliveCountScaling.noDeathBonusPerLevel();
        long limit = maxAliveCountScaling.max() < 0 ? Integer.MAX_VALUE : Math.max(maxAliveCount, maxAliveCountScaling.max());
        return (int) Math.min(limit, maxAliveCount + (long) Math.floor(bonus));
    }

    public record MaxAliveCountScaling(double quotaBonus, double noDeathBonusPerLevel, int max) {
        public static final MaxAliveCountScaling NONE = new MaxAliveCountScaling(0.0D, 0.0D, -1);
    }
}
