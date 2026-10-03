package net.azisaba.aziCave.entity;

import net.azisaba.aziCave.AziCave;
import net.azisaba.aziCave.config.MobProfileSettings;
import net.azisaba.aziCave.config.MobSpawnLightSettings;
import net.azisaba.aziCave.config.MobSpawnSettings;
import net.azisaba.aziCave.config.TorchSpawnPenaltySettings;
import net.azisaba.aziCave.game.GameSession;
import net.azisaba.aziCave.game.RoundState;
import net.azisaba.aziCave.game.SessionState;
import net.azisaba.aziCave.dungeon.PlacedPiece;
import net.azisaba.aziCave.math.BlockBox;
import net.azisaba.aziCave.math.IntVector3;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class MobSpawnManager {
    private final AziCave plugin;
    private final MobAiManager mobAiManager;

    public MobSpawnManager(AziCave plugin, MobAiManager mobAiManager) {
        this.plugin = plugin;
        this.mobAiManager = mobAiManager;
    }

    public BukkitTask start(GameSession session) {
        return new BukkitRunnable() {
            private final Map<UUID, Long> farSince = new HashMap<>();
            private long elapsedTicks;
            private long lastWaveTicks;

            @Override
            public void run() {
                elapsedTicks += 20L;
                MobSpawnSettings settings = plugin.settings().azicave().mobSpawn();
                List<Location> players = exploringPlayers(session);
                List<LivingEntity> mobs = new ArrayList<>(livingMobs(session.world()));
                int beforeDespawn = mobs.size();
                despawnDistantMobs(mobs, players, settings.despawn(), farSince, elapsedTicks);
                if (mobs.size() < beforeDespawn) {
                    plugin.debugLogger().log("mob_spawn", "despawned", Map.of(
                            "session", session.sessionId(), "world", session.world().getName(),
                            "count", beforeDespawn - mobs.size(), "aliveCount", mobs.size(),
                            "reason", "distant_from_players"));
                }
                if (elapsedTicks - lastWaveTicks >= settings.intervalTicks()) {
                    lastWaveTicks = elapsedTicks;
                    spawnWave(session, players, mobs, settings);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public MobStatus mobStatus(GameSession session) {
        MobSpawnSettings settings = plugin.settings().azicave().mobSpawn();
        List<Location> players = exploringPlayers(session);
        List<LivingEntity> mobs = livingMobs(session.world());
        int depth = session.getMaxDepth();
        return new MobStatus(depth, players.size(), settings.scaledMaxAlivePower(depth, players.size()),
                currentAlivePower(mobs, settings), mobs.size(), Map.copyOf(aliveCounts(mobs)));
    }

    private static List<LivingEntity> livingMobs(World world) {
        return world.getLivingEntities().stream()
                .filter(entity -> entity.isValid() && !entity.isDead()
                        && entity.getScoreboardTags().contains(MobProfile.MOB_TAG))
                .toList();
    }

    private static Map<MobProfile, Integer> aliveCounts(List<LivingEntity> mobs) {
        Map<MobProfile, Integer> counts = new EnumMap<>(MobProfile.class);
        for (LivingEntity mob : mobs) {
            MobProfile profile = MobProfile.fromEntity(mob);
            if (profile != null) counts.merge(profile, 1, Integer::sum);
        }
        return counts;
    }

    public record MobStatus(int depth, int exploringPlayers, int maxAlivePower, int alivePower,
                            int aliveCount, Map<MobProfile, Integer> aliveCounts) {
    }

    static List<Location> exploringPlayers(GameSession session) {
        if (session.state() != SessionState.IN_ROUND || session.roundState() != RoundState.ACTIVE
                || session.isBossBattleActive()) {
            return List.of();
        }
        return session.world().getPlayers().stream()
                .filter(player -> session.alivePlayers().contains(player.getUniqueId())
                        && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR)
                .map(player -> player.getLocation())
                .filter(location -> !session.homeArea().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())
                        && session.resolveContainingDepth(location).isPresent())
                .toList();
    }

    static double nearestDistanceSquared(Location location, List<Location> players) {
        return players.stream().mapToDouble(location::distanceSquared).min().orElse(Double.POSITIVE_INFINITY);
    }

    static void despawnDistantMobs(List<LivingEntity> mobs, List<Location> players, MobSpawnSettings.Despawn settings,
                                   Map<UUID, Long> farSince, long nowTicks) {
        if (!settings.enabled()) {
            farSince.clear();
            return;
        }
        farSince.keySet().retainAll(mobs.stream().map(Entity::getUniqueId).collect(Collectors.toSet()));
        mobs.removeIf(mob -> {
            UUID id = mob.getUniqueId();
            if (nearestDistanceSquared(mob.getLocation(), players) < settings.minPlayerDistance() * settings.minPlayerDistance()) {
                farSince.remove(id);
                return false;
            }
            long since = farSince.computeIfAbsent(id, ignored -> nowTicks);
            if (nowTicks - since < settings.afterSeconds() * 20L) {
                return false;
            }
            PathfindTargetRegistry.clear(mob);
            mob.remove();
            farSince.remove(id);
            return true;
        });
    }

    private void spawnWave(GameSession session, List<Location> players, List<LivingEntity> mobs, MobSpawnSettings mobSpawnSettings) {
        if (players.isEmpty() && !plugin.debugLogger().isEnabled()) {
            return;
        }
        Random random = ThreadLocalRandom.current();
        int depth = session.getMaxDepth();
        int maxAlivePower = mobSpawnSettings.scaledMaxAlivePower(depth, players.size());
        int alivePower = currentAlivePower(mobs, mobSpawnSettings);
        Map<MobProfile, Integer> aliveCounts = aliveCounts(mobs);
        if (plugin.debugLogger().isEnabled()) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (MobProfile profile : MobProfile.values()) {
                counts.put(profile.key(), aliveCounts.getOrDefault(profile, 0));
            }
            plugin.debugLogger().log("mob_spawn", "wave_status", Map.ofEntries(
                    Map.entry("session", session.sessionId()), Map.entry("world", session.world().getName()),
                    Map.entry("state", session.state()), Map.entry("roundState", session.roundState()),
                    Map.entry("bossBattle", session.isBossBattleActive()), Map.entry("depth", depth),
                    Map.entry("players", players.size()), Map.entry("baseMaxAlivePower", mobSpawnSettings.maxAlivePower()),
                    Map.entry("maxAlivePower", maxAlivePower), Map.entry("alivePower", alivePower),
                    Map.entry("aliveCount", mobs.size()), Map.entry("aliveCounts", counts)));
        }
        String skipReason = players.isEmpty() ? "no_exploring_players"
                : maxAlivePower <= 0 ? "max_power_disabled"
                : alivePower >= maxAlivePower ? "global_power_limit" : null;
        if (skipReason != null) {
            plugin.debugLogger().log("mob_spawn", "wave_skipped", Map.of(
                    "session", session.sessionId(), "reason", skipReason));
            return;
        }
        Map<Location, Integer> nearbyPowers = new LinkedHashMap<>();
        double radiusSquared = mobSpawnSettings.nearbyLimit().radius() * mobSpawnSettings.nearbyLimit().radius();
        for (Location player : players) {
            nearbyPowers.put(player, currentAlivePower(mobs.stream()
                    .filter(mob -> player.distanceSquared(mob.getLocation()) <= radiusSquared).toList(), mobSpawnSettings));
        }
        if (plugin.debugLogger().isEnabled() && mobSpawnSettings.nearbyLimit().enabled()) {
            for (Map.Entry<Location, Integer> entry : nearbyPowers.entrySet()) {
                plugin.debugLogger().log("mob_spawn", "nearby_status", Map.of(
                        "session", session.sessionId(), "playerLocation", entry.getKey().toVector(),
                        "alivePower", entry.getValue(), "maxAlivePower", mobSpawnSettings.nearbyLimit().maxAlivePower(),
                        "radius", mobSpawnSettings.nearbyLimit().radius()));
            }
        }
        List<PlacedPiece> rooms = session.placedPieces().stream()
                .filter(piece -> !mobSpawnSettings.playerDistance().enabled()
                        || isRoomNearPlayers(piece.worldBounds(), players, mobSpawnSettings.playerDistance().maxDistance()))
                .toList();

        int spawned = 0;
        int noLocation = 0;
        int noProfile = 0;
        int failed = 0;
        for (int index = 0; index < mobSpawnSettings.countPerInterval() && alivePower < maxAlivePower; index++) {
            int globalRemainingPower = maxAlivePower - alivePower;
            Predicate<Location> allowed = location -> mobSpawnSettings.playerDistance().allows(nearestDistanceSquared(location, players))
                    && remainingSpawnPower(location, nearbyPowers, mobSpawnSettings.nearbyLimit(), globalRemainingPower) > 0;
            Location spawnLocation = findSpawnLocation(session.world(), rooms, random, mobSpawnSettings.light(), allowed);
            if (spawnLocation == null) {
                noLocation++;
                continue;
            }
            int remainingPower = remainingSpawnPower(spawnLocation, nearbyPowers, mobSpawnSettings.nearbyLimit(), globalRemainingPower);
            MobProfile profile = mobSpawnSettings.selectRandomProfile(random, remainingPower, aliveCounts);
            if (profile == null) {
                noProfile++;
                continue;
            }
            MobProfileSettings profileSettings = mobSpawnSettings.profile(profile);

            if (spawnMob(session, spawnLocation, profile) != null) {
                spawned++;
                alivePower += profileSettings.power();
                aliveCounts.merge(profile, 1, Integer::sum);
                nearbyPowers.replaceAll((player, power) -> player.distanceSquared(spawnLocation) <= radiusSquared
                        ? (int) Math.min(Integer.MAX_VALUE, (long) power + profileSettings.power()) : power);
            } else {
                failed++;
            }
        }
        plugin.debugLogger().log("mob_spawn", "wave_complete", Map.of(
                "session", session.sessionId(), "candidateRooms", rooms.size(),
                "spawned", spawned, "noLocation", noLocation, "noProfile", noProfile, "failed", failed,
                "alivePower", alivePower, "maxAlivePower", maxAlivePower,
                "aliveCount", mobs.size() + spawned, "aliveCounts", aliveCounts));
    }

    public LivingEntity spawnMob(GameSession session, Location location, MobProfile profile) {
        if (!session.world().equals(location.getWorld())) {
            throw new IllegalArgumentException("Spawn location must be in the session world");
        }
        MobProfileSettings settings = plugin.settings().azicave().mobSpawn().profile(profile);
        Entity entity = session.world().spawnEntity(location, profile.entityType());
        if (!(entity instanceof LivingEntity mob)) {
            entity.remove();
            plugin.debugLogger().log("mob_spawn", "spawn_failed", Map.of(
                    "session", session.sessionId(), "profile", profile.key(), "location", location.toVector(),
                    "reason", "not_living_entity"));
            return null;
        }
        try {
            profile.apply(mob, settings);
            if (mob instanceof Mob aiMob) {
                double strollSpeed = aiMob instanceof Creaking ? settings.ai().creaking().strollSpeed() : 1.0D;
                Bukkit.getMobGoals().addGoal(aiMob, 6, new RandomStrollGoal(aiMob, strollTargets(session), strollSpeed));
                if (aiMob instanceof Creaking creaking && settings.ai().enabled()) {
                    Bukkit.getMobGoals().addGoal(creaking, 2, new CreakingChaseGoal(creaking, settings.ai().creaking()));
                }
            }
            mobAiManager.track(mob, profile);
            if (plugin.debugLogger().isEnabled()) {
                plugin.debugLogger().log("mob_spawn", "spawned", Map.of(
                        "session", session.sessionId(), "world", session.world().getName(),
                        "profile", profile.key(), "entity", mob.getUniqueId(), "power", settings.power(),
                        "location", location.toVector(), "depth", session.resolveDepth(location)));
            }
            return mob;
        } catch (RuntimeException ex) {
            entity.remove();
            plugin.debugLogger().log("mob_spawn", "spawn_failed", Map.of(
                    "session", session.sessionId(), "profile", profile.key(), "location", location.toVector(),
                    "reason", ex.toString()));
            throw ex;
        }
    }

    private int currentAlivePower(List<LivingEntity> mobs, MobSpawnSettings settings) {
        long totalPower = 0;
        for (LivingEntity entity : mobs) {
            MobProfile profile = MobProfile.fromEntity(entity);
            if (profile != null) {
                totalPower += settings.profile(profile).power();
            }
        }
        return (int) Math.min(Integer.MAX_VALUE, totalPower);
    }

    static int remainingSpawnPower(Location location, Map<Location, Integer> nearbyPowers,
                                   MobSpawnSettings.NearbyLimit settings, int globalRemainingPower) {
        int remaining = globalRemainingPower;
        if (settings.enabled()) {
            for (Map.Entry<Location, Integer> entry : nearbyPowers.entrySet()) {
                if (entry.getKey().distanceSquared(location) <= settings.radius() * settings.radius()) {
                    remaining = Math.min(remaining, settings.maxAlivePower() - entry.getValue());
                }
            }
        }
        return Math.max(0, remaining);
    }

    static boolean isRoomNearPlayers(BlockBox bounds, List<Location> players, double maxDistance) {
        for (Location player : players) {
            double dx = player.getX() - Math.clamp(player.getX(), bounds.minX(), bounds.maxX() + 1.0D);
            double dy = player.getY() - Math.clamp(player.getY(), bounds.minY(), bounds.maxY() + 1.0D);
            double dz = player.getZ() - Math.clamp(player.getZ(), bounds.minZ(), bounds.maxZ() + 1.0D);
            if (dx * dx + dy * dy + dz * dz <= maxDistance * maxDistance) return true;
        }
        return false;
    }

    private Location findSpawnLocation(World world, List<PlacedPiece> rooms, Random random,
                                       MobSpawnLightSettings lightSettings, Predicate<Location> allowed) {
        if (rooms.isEmpty()) {
            return null;
        }

        if (!lightSettings.enabled()) {
            for (int attempt = 0; attempt < Math.max(8, rooms.size() * 2); attempt++) {
                PlacedPiece piece = rooms.get(random.nextInt(rooms.size()));
                Location location = findSpawnLocation(world, piece.worldBounds(), random, allowed);
                if (location != null) {
                    return location;
                }
            }
            return null;
        }

        List<SpawnCandidate> candidates = new ArrayList<>();
        double totalWeight = 0.0D;
        int attempts = Math.max(lightSettings.sampleAttemptsPerSpawn(), rooms.size() * 2);
        for (int attempt = 0; attempt < attempts; attempt++) {
            PlacedPiece piece = rooms.get(random.nextInt(rooms.size()));
            Location location = findSpawnLocation(world, piece.worldBounds(), random, allowed);
            if (location == null) {
                continue;
            }
            double weight = spawnWeight(location.getBlock(), lightSettings);
            if (weight <= 0.0D) {
                continue;
            }
            candidates.add(new SpawnCandidate(location, weight));
            totalWeight += weight;
        }

        if (candidates.isEmpty() || totalWeight <= 0.0D) {
            return null;
        }

        double cursor = random.nextDouble() * totalWeight;
        for (SpawnCandidate candidate : candidates) {
            cursor -= candidate.weight();
            if (cursor <= 0.0D) {
                return candidate.location();
            }
        }
        return candidates.getLast().location();
    }

    private List<Location> strollTargets(GameSession session) {
        List<Location> targets = new ArrayList<>();
        for (IntVector3 point : session.dungeonStrollPoints()) {
            targets.add(new Location(session.world(), point.x() + 0.5D, point.y(), point.z() + 0.5D));
        }
        return targets;
    }

    private Location findSpawnLocation(World world, BlockBox bounds, Random random, Predicate<Location> allowed) {
        int minX = interiorMin(bounds.minX(), bounds.maxX());
        int maxX = interiorMax(bounds.minX(), bounds.maxX());
        int minZ = interiorMin(bounds.minZ(), bounds.maxZ());
        int maxZ = interiorMax(bounds.minZ(), bounds.maxZ());
        int minY = Math.max(bounds.minY() + 1, world.getMinHeight() + 1);
        int maxY = Math.min(bounds.maxY() - 1, world.getMaxHeight() - 2);

        for (int attempt = 0; attempt < 16; attempt++) {
            int x = randomBetween(minX, maxX, random);
            int z = randomBetween(minZ, maxZ, random);
            for (int y = minY; y <= maxY; y++) {
                Location location = new Location(world, x + 0.5D, y, z + 0.5D);
                if (!allowed.test(location)) {
                    continue;
                }
                Block feet = world.getBlockAt(x, y, z);
                if (!isValidSpawnBlock(feet)) {
                    continue;
                }
                return location;
            }
        }
        return null;
    }

    private boolean isValidSpawnBlock(Block block) {
        Block floor = block.getRelative(BlockFace.DOWN);
        Block head = block.getRelative(BlockFace.UP);
        return floor.getType().isSolid() && block.isPassable() && head.isPassable() && floor.getLightFromSky() == 0;
    }

    private double spawnWeight(Block feet, MobSpawnLightSettings settings) {
        double lightWeight = blockLightWeight(feet, settings);
        double torchMultiplier = torchMultiplier(feet, settings);
        return Math.max(0.0D, lightWeight * torchMultiplier);
    }

    private double blockLightWeight(Block feet, MobSpawnLightSettings settings) {
        int maxEffectiveLight = settings.maxEffectiveBlockLight();
        if (maxEffectiveLight <= 0) {
            return 1.0D;
        }

        double lightRatio = Math.min(feet.getLightFromBlocks(), maxEffectiveLight) / (double) maxEffectiveLight;
        double darkness = Math.max(0.0D, 1.0D - lightRatio);
        double weightedDarkness = Math.pow(darkness, settings.curvePower());
        return Math.max(settings.minWeight(), weightedDarkness);
    }

    private double torchMultiplier(Block feet, MobSpawnLightSettings settings) {
        double multiplier = 1.0D;
        for (TorchSpawnPenaltySettings torchSettings : settings.torchTypes().values()) {
            if (torchSettings.radius() <= 0 || torchSettings.multiplier() >= 1.0D) {
                continue;
            }
            if (hasNearbyMaterial(feet, torchSettings.materials(), torchSettings.radius())) {
                multiplier *= torchSettings.multiplier();
            }
        }
        return multiplier;
    }

    private boolean hasNearbyMaterial(Block center, java.util.Set<Material> materials, int radius) {
        if (materials.isEmpty()) {
            return false;
        }

        World world = center.getWorld();
        int minY = Math.max(world.getMinHeight(), center.getY() - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, center.getY() + radius);
        int radiusSquared = radius * radius;
        for (int x = center.getX() - radius; x <= center.getX() + radius; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = center.getZ() - radius; z <= center.getZ() + radius; z++) {
                    int dx = x - center.getX();
                    int dy = y - center.getY();
                    int dz = z - center.getZ();
                    if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                        continue;
                    }
                    if (materials.contains(world.getBlockAt(x, y, z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private int randomBetween(int min, int max, Random random) {
        if (max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    private int interiorMin(int min, int max) {
        return max - min >= 2 ? min + 1 : min;
    }

    private int interiorMax(int min, int max) {
        return max - min >= 2 ? max - 1 : max;
    }

    private record SpawnCandidate(Location location, double weight) {
    }
}
