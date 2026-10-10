package net.azisaba.aziCave.config;

import net.azisaba.aziCave.entity.MobProfile;

import java.util.Map;
import java.util.Random;

public record MobSpawnSettings(
        long intervalSeconds,
        int countPerInterval,
        int maxAlivePower,
        PowerScaling powerScaling,
        Diversity diversity,
        Pressure pressure,
        PlayerDistance playerDistance,
        NearbyLimit nearbyLimit,
        Despawn despawn,
        MobSpawnLightSettings light,
        Map<String, MobProfileSettings> profiles
) {
    public long intervalTicks() {
        return intervalSeconds * 20L;
    }

    public int scaledMaxAlivePower(int depth, int players, SpawnPressure spawnPressure) {
        if (maxAlivePower <= 0) {
            return maxAlivePower;
        }
        double multiplier = spawnPressure.powerMultiplier();
        if (powerScaling.enabled()) {
            double depthMultiplier = Math.min(powerScaling.depthMaxMultiplier(),
                    1.0D + Math.max(0, depth) * powerScaling.depthPerLevel());
            double playersMultiplier = Math.min(powerScaling.playersMaxMultiplier(),
                    1.0D + Math.max(0, players - 1) * powerScaling.playersPerAdditionalPlayer());
            multiplier *= depthMultiplier * playersMultiplier;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(maxAlivePower * multiplier));
    }

    public int scaledCountPerInterval(SpawnPressure spawnPressure) {
        return (int) Math.min(Integer.MAX_VALUE, (long) countPerInterval + spawnPressure.countBonus());
    }

    public record PowerScaling(boolean enabled, double depthPerLevel, double depthMaxMultiplier,
                               double playersPerAdditionalPlayer, double playersMaxMultiplier) {
    }

    public record Diversity(boolean enabled, double penaltyPerAlive, double minMultiplier, double absentBonusMultiplier) {
        public double multiplier(int aliveCount) {
            if (!enabled) {
                return 1.0D;
            }
            if (aliveCount <= 0) {
                return absentBonusMultiplier;
            }
            return Math.max(minMultiplier, 1.0D / (1.0D + aliveCount * penaltyPerAlive));
        }
    }

    public record Pressure(QuotaPressure quota, NoDeathPressure noDeath) {
        public SpawnPressure evaluate(long delivered, long quotaAmount, int daysWithoutDeath) {
            int quotaLevel = quota.level(delivered, quotaAmount);
            int noDeathLevel = noDeath.level(daysWithoutDeath);
            double countBonus = quotaLevel * quota.countBonus() + noDeathLevel * noDeath.countBonusPerLevel();
            double powerMultiplier = (quotaLevel > 0 ? quota.powerMultiplier() : 1.0D)
                    * (1.0D + noDeathLevel * noDeath.powerMultiplierPerLevel());
            double strongMobBias = quotaLevel * quota.strongMobBias() + noDeathLevel * noDeath.strongMobBiasPerLevel();
            return new SpawnPressure(quotaLevel, noDeathLevel,
                    (int) Math.min(Integer.MAX_VALUE, Math.floor(countBonus)), powerMultiplier, strongMobBias);
        }
    }

    public record QuotaPressure(boolean enabled, double thresholdPercent, double countBonus,
                                double powerMultiplier, double strongMobBias) {
        public int level(long delivered, long quotaAmount) {
            return enabled && quotaAmount > 0L && delivered * 100.0D >= quotaAmount * thresholdPercent ? 1 : 0;
        }
    }

    public record NoDeathPressure(boolean enabled, int minDays, int maxLevels, double countBonusPerLevel,
                                  double powerMultiplierPerLevel, double strongMobBiasPerLevel) {
        public int level(int daysWithoutDeath) {
            return enabled ? Math.clamp((long) daysWithoutDeath - minDays + 1L, 0, maxLevels) : 0;
        }
    }

    public record SpawnPressure(int quotaLevel, int noDeathLevel, int countBonus, double powerMultiplier,
                                double strongMobBias) {
        public static final SpawnPressure NONE = new SpawnPressure(0, 0, 0, 1.0D, 0.0D);
    }

    public record PlayerDistance(boolean enabled, double minDistance, double maxDistance) {
        public boolean allows(double nearestDistanceSquared) {
            return !enabled || (nearestDistanceSquared >= minDistance * minDistance
                    && nearestDistanceSquared <= maxDistance * maxDistance);
        }
    }

    public record NearbyLimit(boolean enabled, double radius, int maxAlivePower) {
    }

    public record Despawn(boolean enabled, double minPlayerDistance, long afterSeconds) {
    }

    public MobProfileSettings profile(MobProfile profile) {
        MobProfileSettings settings = profiles.get(profile.key());
        return settings == null ? profile.defaultSettings() : settings;
    }

    public MobProfile selectRandomProfile(Random random, int remainingPower, Map<MobProfile, Integer> aliveCounts,
                                          SpawnPressure spawnPressure) {
        if (remainingPower <= 0) {
            return null;
        }

        MobProfile[] candidates = MobProfile.values();
        double[] weights = new double[candidates.length];
        double totalWeight = 0.0D;
        for (int index = 0; index < candidates.length; index++) {
            weights[index] = spawnWeight(candidates[index], remainingPower, aliveCounts, spawnPressure);
            totalWeight += weights[index];
        }
        if (totalWeight <= 0.0D) {
            return null;
        }

        double cursor = random.nextDouble() * totalWeight;
        MobProfile last = null;
        for (int index = 0; index < candidates.length; index++) {
            if (weights[index] <= 0.0D) {
                continue;
            }
            last = candidates[index];
            cursor -= weights[index];
            if (cursor < 0.0D) {
                return last;
            }
        }
        return last;
    }

    /**
     * 抽選用の重み = weight × 多様性補正 × 強モブ補正。スポーン不可の場合は0。
     */
    public double spawnWeight(MobProfile profile, int remainingPower, Map<MobProfile, Integer> aliveCounts,
                              SpawnPressure spawnPressure) {
        MobProfileSettings settings = profile(profile);
        if (settings.weight() <= 0 || settings.power() > remainingPower) {
            return 0.0D;
        }
        int aliveCount = aliveCounts.getOrDefault(profile, 0);
        int maxAliveCount = settings.scaledMaxAliveCount(spawnPressure);
        if (maxAliveCount >= 0 && aliveCount >= maxAliveCount) {
            return 0.0D;
        }
        return settings.weight() * diversity.multiplier(aliveCount)
                * strongMobMultiplier(settings.power(), spawnPressure.strongMobBias());
    }

    /**
     * (power ÷ 抽選対象の最小power) ^ bias。biasが0なら補正なし。
     */
    public double strongMobMultiplier(int power, double bias) {
        if (bias <= 0.0D) {
            return 1.0D;
        }
        int basePower = Integer.MAX_VALUE;
        for (MobProfile profile : MobProfile.values()) {
            MobProfileSettings settings = profile(profile);
            if (settings.weight() > 0) {
                basePower = Math.min(basePower, Math.max(1, settings.power()));
            }
        }
        return Math.pow(Math.max(1, power) / (double) basePower, bias);
    }
}
