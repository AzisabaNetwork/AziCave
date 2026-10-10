package net.azisaba.aziCave.config;

import net.azisaba.aziCave.config.MobSpawnSettings.SpawnPressure;
import net.azisaba.aziCave.entity.MobProfile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MobSpawnSettingsTest {
    private static final MobSpawnSettings.Diversity DIVERSITY = new MobSpawnSettings.Diversity(true, 0.5D, 0.1D, 1.5D);
    private static final MobSpawnSettings.Pressure PRESSURE = new MobSpawnSettings.Pressure(
            new MobSpawnSettings.QuotaPressure(true, 50.0D, 1.0D, 1.25D, 1.0D),
            new MobSpawnSettings.NoDeathPressure(true, 2, 3, 0.5D, 0.5D, 0.5D));

    @Test
    void diversityFavorsAbsentProfilesAndPenalizesCommonOnes() {
        assertEquals(1.5D, DIVERSITY.multiplier(0));
        assertEquals(0.5D, DIVERSITY.multiplier(2));
        assertEquals(0.1D, DIVERSITY.multiplier(100));
        assertEquals(1.0D, new MobSpawnSettings.Diversity(false, 0.5D, 0.1D, 1.5D).multiplier(2));
    }

    @Test
    void quotaPressureStartsAtTheConfiguredPercentage() {
        assertEquals(0, PRESSURE.evaluate(49L, 100L, 0).quotaLevel());
        assertEquals(1, PRESSURE.evaluate(50L, 100L, 0).quotaLevel());
        assertEquals(0, PRESSURE.evaluate(50L, 0L, 0).quotaLevel());
    }

    @Test
    void noDeathPressureGrowsPerDayUpToTheMaximumLevel() {
        assertEquals(0, PRESSURE.evaluate(0L, 100L, 1).noDeathLevel());
        assertEquals(1, PRESSURE.evaluate(0L, 100L, 2).noDeathLevel());
        assertEquals(3, PRESSURE.evaluate(0L, 100L, 10).noDeathLevel());
    }

    @Test
    void pressuresStack() {
        SpawnPressure pressure = PRESSURE.evaluate(100L, 100L, 3);

        assertEquals(2, pressure.countBonus());
        assertEquals(2.5D, pressure.powerMultiplier());
        assertEquals(2.0D, pressure.strongMobBias());
        assertEquals(SpawnPressure.NONE, PRESSURE.evaluate(0L, 100L, 0));
    }

    @Test
    void pressureRaisesSpawnCountAndPowerLimit() {
        MobSpawnSettings settings = settings(Map.of());
        SpawnPressure pressure = PRESSURE.evaluate(100L, 100L, 3);

        assertEquals(1, settings.scaledCountPerInterval(SpawnPressure.NONE));
        assertEquals(3, settings.scaledCountPerInterval(pressure));
        assertEquals(16, settings.scaledMaxAlivePower(5, 2, SpawnPressure.NONE));
        assertEquals(40, settings.scaledMaxAlivePower(5, 2, pressure));
    }

    @Test
    void maxAliveCountScalesWithPressureUpToItsLimit() {
        MobProfileSettings golem = profile(3, 4, 2, new MobProfileSettings.MaxAliveCountScaling(1.0D, 0.5D, 4));
        MobProfileSettings unlimited = profile(3, 4, -1, new MobProfileSettings.MaxAliveCountScaling(1.0D, 0.5D, 4));

        assertEquals(2, golem.scaledMaxAliveCount(SpawnPressure.NONE));
        assertEquals(3, golem.scaledMaxAliveCount(PRESSURE.evaluate(100L, 100L, 0)));
        assertEquals(4, golem.scaledMaxAliveCount(PRESSURE.evaluate(100L, 100L, 10)));
        assertEquals(-1, unlimited.scaledMaxAliveCount(PRESSURE.evaluate(100L, 100L, 10)));
    }

    @Test
    void spawnWeightCombinesDiversityStrongBiasAndLimits() {
        Map<String, MobProfileSettings> profiles = new LinkedHashMap<>();
        for (MobProfile profile : MobProfile.values()) {
            profiles.put(profile.key(), profile(0, 1, -1, MobProfileSettings.MaxAliveCountScaling.NONE));
        }
        profiles.put(MobProfile.ZOMBIE_BRUTE.key(), profile(6, 4, -1, MobProfileSettings.MaxAliveCountScaling.NONE));
        profiles.put(MobProfile.POWERED_CREEPER.key(), profile(3, 2, -1, MobProfileSettings.MaxAliveCountScaling.NONE));
        profiles.put(MobProfile.CREAKING.key(), profile(3, 4, 1, new MobProfileSettings.MaxAliveCountScaling(1.0D, 0.0D, 2)));
        MobSpawnSettings settings = settings(profiles);
        Map<MobProfile, Integer> alive = Map.of(MobProfile.ZOMBIE_BRUTE, 2, MobProfile.CREAKING, 1);
        SpawnPressure quota = PRESSURE.evaluate(100L, 100L, 0);

        assertEquals(3.0D, settings.spawnWeight(MobProfile.ZOMBIE_BRUTE, 8, alive, SpawnPressure.NONE));
        assertEquals(6.0D, settings.spawnWeight(MobProfile.ZOMBIE_BRUTE, 8, alive, quota));
        assertEquals(4.5D, settings.spawnWeight(MobProfile.POWERED_CREEPER, 8, alive, quota));
        assertEquals(0.0D, settings.spawnWeight(MobProfile.ZOMBIE_BRUTE, 3, alive, quota));
        assertEquals(0.0D, settings.spawnWeight(MobProfile.CREAKING, 8, alive, SpawnPressure.NONE));
        assertEquals(4.0D, settings.spawnWeight(MobProfile.CREAKING, 8, alive, quota), 1.0E-9D);
    }

    private static MobProfileSettings profile(int weight, int power, int maxAliveCount,
                                              MobProfileSettings.MaxAliveCountScaling scaling) {
        MobProfileSettings defaults = MobProfile.ZOMBIE_BRUTE.defaultSettings();
        return new MobProfileSettings(weight, power, 20.0D, 0.3D, 4.0D, maxAliveCount, scaling, defaults.ai(), defaults.drops());
    }

    private static MobSpawnSettings settings(Map<String, MobProfileSettings> profiles) {
        return new MobSpawnSettings(30L, 1, 16,
                new MobSpawnSettings.PowerScaling(false, 0.5D, 3.0D, 0.5D, 3.0D),
                DIVERSITY, PRESSURE,
                new MobSpawnSettings.PlayerDistance(true, 8.0D, 32.0D),
                new MobSpawnSettings.NearbyLimit(true, 32.0D, 8),
                new MobSpawnSettings.Despawn(true, 64.0D, 60L),
                null, profiles);
    }
}
