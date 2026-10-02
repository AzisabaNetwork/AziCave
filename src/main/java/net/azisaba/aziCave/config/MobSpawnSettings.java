package net.azisaba.aziCave.config;

import net.azisaba.aziCave.entity.MobProfile;

import java.util.Map;
import java.util.Random;

public record MobSpawnSettings(
        long intervalSeconds,
        int countPerInterval,
        int maxAlivePower,
        PowerScaling powerScaling,
        PlayerDistance playerDistance,
        NearbyLimit nearbyLimit,
        Despawn despawn,
        MobSpawnLightSettings light,
        Map<String, MobProfileSettings> profiles
) {
    public long intervalTicks() {
        return intervalSeconds * 20L;
    }

    public int scaledMaxAlivePower(int depth, int players) {
        if (!powerScaling.enabled() || maxAlivePower <= 0) {
            return maxAlivePower;
        }
        double depthMultiplier = Math.min(powerScaling.depthMaxMultiplier(),
                1.0D + Math.max(0, depth) * powerScaling.depthPerLevel());
        double playersMultiplier = Math.min(powerScaling.playersMaxMultiplier(),
                1.0D + Math.max(0, players - 1) * powerScaling.playersPerAdditionalPlayer());
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(maxAlivePower * depthMultiplier * playersMultiplier));
    }

    public record PowerScaling(boolean enabled, double depthPerLevel, double depthMaxMultiplier,
                               double playersPerAdditionalPlayer, double playersMaxMultiplier) {
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

    public MobProfile selectRandomProfile(Random random, int remainingPower, Map<MobProfile, Integer> aliveCounts) {
        if (remainingPower <= 0) {
            return null;
        }

        int totalWeight = 0;
        for (MobProfile profile : MobProfile.values()) {
            MobProfileSettings settings = profile(profile);
            if (!canSpawnProfile(profile, settings, remainingPower, aliveCounts)) {
                continue;
            }
            totalWeight += settings.weight();
        }
        if (totalWeight <= 0) {
            return null;
        }

        int cursor = random.nextInt(totalWeight);
        for (MobProfile profile : MobProfile.values()) {
            MobProfileSettings settings = profile(profile);
            if (!canSpawnProfile(profile, settings, remainingPower, aliveCounts)) {
                continue;
            }
            cursor -= settings.weight();
            if (cursor < 0) {
                return profile;
            }
        }
        return null;
    }

    private boolean canSpawnProfile(
            MobProfile profile,
            MobProfileSettings settings,
            int remainingPower,
            Map<MobProfile, Integer> aliveCounts
    ) {
        if (settings.weight() <= 0 || settings.power() > remainingPower) {
            return false;
        }
        int maxAliveCount = settings.maxAliveCount();
        return maxAliveCount < 0 || aliveCounts.getOrDefault(profile, 0) < maxAliveCount;
    }
}
