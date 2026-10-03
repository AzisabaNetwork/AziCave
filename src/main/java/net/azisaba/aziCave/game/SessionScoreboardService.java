package net.azisaba.aziCave.game;

import net.azisaba.aziCave.AziCave;
import net.azisaba.aziCave.dungeon.DisplayGimmickKeys;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class SessionScoreboardService {
    private static final String OBJECTIVE_NAME = "azicave";
    private static final String SERVER_ADDRESS = "azisaba.net";
    private final AziCave plugin;
    private final GameSessionManager sessionManager;
    private final Map<UUID, Scoreboard> playerScoreboards = new HashMap<>();
    private BukkitTask task;
    private int debugUpdates;

    public SessionScoreboardService(AziCave plugin, GameSessionManager sessionManager) {
        this.plugin = plugin;
        this.sessionManager = sessionManager;
    }

    public void start() {
        if (task != null) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            clearLegacyScoreboard(manager.getMainScoreboard());
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 1L, 20L);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID playerId : Set.copyOf(playerScoreboards.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                clear(player);
            }
        }
        playerScoreboards.clear();
    }

    private void updateAll() {
        boolean debug = plugin.debugLogger().isEnabled();
        if (!debug) {
            debugUpdates = 0;
        }
        if (debug && debugUpdates == 0) {
            plugin.debugLogger().log("nametag", "environment", Map.of(
                    "server", Bukkit.getVersion(),
                    "plugins", Arrays.stream(Bukkit.getPluginManager().getPlugins())
                            .map(installed -> installed.getName() + ":" + installed.getDescription().getVersion())
                            .collect(Collectors.joining(","))
            ));
        }
        boolean sample = debug && debugUpdates++ % 5 == 0;
        if (sample) {
            logNameTags("before_update");
        }
        Set<UUID> onlinePlayers = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            onlinePlayers.add(player.getUniqueId());
            GameSession session = sessionManager.sessionForPlayer(player.getUniqueId()).orElse(null);
            if (session == null) {
                if (playerScoreboards.containsKey(player.getUniqueId())) {
                    clear(player);
                }
                continue;
            }
            show(player, session);
        }
        playerScoreboards.keySet().removeIf(playerId -> !onlinePlayers.contains(playerId));
        if (sample) {
            logNameTags("after_update");
            Bukkit.getScheduler().runTaskLater(plugin, () -> logNameTags("next_tick"), 1L);
        }
        sessionManager.sessions().forEach(session -> {
            showAngerParticles(session);
            showTrapParticles(session);
        });
    }

    private void showTrapParticles(GameSession session) {
        for (BlockDisplay trap : session.world().getEntitiesByClass(BlockDisplay.class)) {
            if (!trap.getScoreboardTags().contains(DisplayGimmickKeys.TRAP_DISPLAY_TAG)) {
                continue;
            }
            Location location = trap.getLocation().add(0.5D, 0.35D, 0.5D);
            for (Player player : session.world().getPlayers()) {
                if (player.getLocation().distanceSquared(location) <= 144.0D) {
                    player.spawnParticle(Particle.DUST, location, 6, 0.22D, 0.12D, 0.22D,
                            new Particle.DustOptions(Color.RED, 1.4F));
                    player.spawnParticle(Particle.FLAME, location, 3, 0.18D, 0.1D, 0.18D, 0.0D);
                }
            }
        }
    }

    private void showAngerParticles(GameSession session) {
        if (session.consecutiveQuotaMisses() <= 0) {
            return;
        }
        for (Villager villager : session.world().getEntitiesByClass(Villager.class)) {
            Location location = villager.getLocation();
            if (!session.homeArea().contains(location.getBlockX(), location.getBlockY(), location.getBlockZ())) {
                continue;
            }
            Location above = location.add(0.0D, 2.1D, 0.0D);
            for (UUID playerId : session.onlineMembers()) {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null && player.getWorld().getUID().equals(session.world().getUID())) {
                    player.spawnParticle(Particle.ANGRY_VILLAGER, above, 4, 0.3D, 0.2D, 0.3D, 0.0D);
                }
            }
        }
    }

    private void show(Player player, GameSession session) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = playerScoreboards.computeIfAbsent(player.getUniqueId(), id -> manager.getNewScoreboard());
        Objective objective = prepareScoreboard(
                scoreboard,
                plugin.messages().text("scoreboard.title", "AziCave"),
                Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toSet())
        );

        Map<String, Integer> lines = new HashMap<>();
        int quotaRound = DepartureGuard.dayToStart(session.currentRound());
        long quota = plugin.economyService().quotaForRound(quotaRound);
        long delivered = session.state() == SessionState.GAME_OVER || session.state() == SessionState.CLOSING
                ? 0L
                : plugin.economyService().deliveryValue(session);
        int maxMisses = plugin.settings().economy().quota().maxConsecutiveMisses();
        lines.put(ChatColor.DARK_GRAY.toString(), 11);
        lines.put(ChatColor.GOLD + plugin.messages().format(
                "scoreboard.day",
                "{round}日目",
                "round", session.currentRound()
        ), 9);
        lines.put(label("shared-money", "共有資金") + session.sharedBalance(), 8);
        lines.put(label("delivery-quota", "納品 / ノルマ") + delivered + " / " + quota, 7);
        lines.put(label("villager-anger", "村人の怒り") + AngerGauge.render(session.consecutiveQuotaMisses(), maxMisses), 6);
        String time = allAlivePlayersInDungeon(session) ? "??:??" : RoundClock.format(session.world().getTime());
        lines.put(label("time", "時刻") + time, 5);
        lines.put(ChatColor.BLACK.toString(), 4);
        Guidance guidance = guidance(session, player, delivered, quota);
        if (!guidance.goal().isBlank()) {
            lines.put(label("goal", "目標") + guidance.goal(), 3);
        }
        if (!guidance.next().isBlank()) {
            lines.put(label("next", "やること") + guidance.next(), 2);
        }
        lines.put(ChatColor.DARK_AQUA.toString(), 1);
        lines.put(ChatColor.AQUA + SERVER_ADDRESS, 0);
        updateLines(scoreboard, objective, lines);

        if (player.getScoreboard() != scoreboard) {
            player.setScoreboard(scoreboard);
        }
    }

    static Objective prepareScoreboard(Scoreboard scoreboard, String title, Set<String> onlineNames) {
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective == null) {
            objective = scoreboard.registerNewObjective(OBJECTIVE_NAME, "dummy", title);
        } else if (!title.equals(objective.getDisplayName())) {
            objective.setDisplayName(title);
        }
        if (objective.getDisplaySlot() != DisplaySlot.SIDEBAR) {
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        Team team = scoreboard.getTeam("azicave_hidden");
        if (team == null) {
            team = scoreboard.registerNewTeam("azicave_hidden");
        }
        if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != Team.OptionStatus.NEVER) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        }
        for (String entry : team.getEntries()) {
            if (!onlineNames.contains(entry)) {
                team.removeEntry(entry);
            }
        }
        for (String name : onlineNames) {
            if (!team.hasEntry(name)) {
                team.addEntry(name);
            }
        }
        return objective;
    }

    private void clear(Player player) {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = playerScoreboards.remove(player.getUniqueId());
        if (manager != null && scoreboard != null && player.getScoreboard() == scoreboard) {
            Scoreboard mainScoreboard = manager.getMainScoreboard();
            clearLegacyScoreboard(mainScoreboard);
            player.setScoreboard(mainScoreboard);
        }
    }

    static void clearLegacyScoreboard(Scoreboard scoreboard) {
        // Older versions left persistent AziCave data on the main scoreboard.
        Objective objective = scoreboard.getObjective(OBJECTIVE_NAME);
        if (objective != null) {
            objective.unregister();
        }
        Team team = scoreboard.getTeam("azicave_hidden");
        if (team != null) {
            team.unregister();
        }
    }

    private void logNameTags(String phase) {
        if (!plugin.debugLogger().isEnabled()) {
            return;
        }
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!playerScoreboards.containsKey(viewer.getUniqueId())
                    && sessionManager.sessionForWorld(viewer.getWorld()).isEmpty()) {
                continue;
            }
            Scoreboard scoreboard = viewer.getScoreboard();
            Scoreboard expected = playerScoreboards.get(viewer.getUniqueId());
            Objective sidebar = scoreboard.getObjective(DisplaySlot.SIDEBAR);
            String targets = viewer.getWorld().getPlayers().stream().map(target -> {
                Team team = scoreboard.getEntryTeam(target.getName());
                return target.getName() + ":" + (team == null ? "none" :
                        team.getName() + "/" + team.getOption(Team.Option.NAME_TAG_VISIBILITY));
            }).collect(Collectors.joining(","));
            plugin.debugLogger().log("nametag", phase, Map.of(
                    "viewer", viewer.getName(),
                    "world", viewer.getWorld().getName(),
                    "mode", viewer.getGameMode(),
                    "board", Integer.toHexString(System.identityHashCode(scoreboard)),
                    "expectedBoard", expected == null ? "none" : Integer.toHexString(System.identityHashCode(expected)),
                    "ownBoard", scoreboard == expected,
                    "mainBoard", scoreboard == Bukkit.getScoreboardManager().getMainScoreboard(),
                    "sidebar", sidebar == null ? "none" : sidebar.getName(),
                    "targets", targets
            ));
        }
    }

    static void updateLines(Scoreboard scoreboard, Objective objective, Map<String, Integer> lines) {
        for (String entry : scoreboard.getEntries()) {
            if (!lines.containsKey(entry)) {
                scoreboard.resetScores(entry);
            }
        }
        lines.forEach((text, score) -> objective.getScore(text).setScore(score));
    }

    private String label(String key, String fallback) {
        return ChatColor.YELLOW + plugin.messages().text("scoreboard." + key, fallback) + ": " + ChatColor.WHITE;
    }

    private boolean allAlivePlayersInDungeon(GameSession session) {
        if (session.state() != SessionState.IN_ROUND || session.alivePlayers().isEmpty()) {
            return false;
        }
        for (UUID playerId : session.alivePlayers()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || isInHomeArea(session, player)) {
                return false;
            }
        }
        return true;
    }

    private boolean isInHomeArea(GameSession session, Player player) {
        return player.getWorld().getUID().equals(session.world().getUID())
                && session.homeArea().contains(
                player.getLocation().getBlockX(),
                player.getLocation().getBlockY(),
                player.getLocation().getBlockZ()
        );
    }

    private Guidance guidance(GameSession session, Player player, long delivered, long quota) {
        if (session.state() == SessionState.GAME_OVER) {
            return new Guidance("", plugin.messages().text("scoreboard.next-actions.leave-session", "セッションを出る"));
        }
        if (session.state() == SessionState.LOBBY) {
            return new Guidance("", plugin.messages().text(
                    session.currentRound() == 0
                            ? "scoreboard.next-actions.start-first-round"
                            : "scoreboard.next-actions.choose-depth",
                    session.currentRound() == 0
                            ? "装備を整えてダンジョンに入ろう！"
                            : "危険度を選んでダンジョンへ"
            ));
        }
        if (session.isBossBattleActive()) {
            return new Guidance(plugin.messages().text("scoreboard.goals.defeat-boss", "ボスを倒す"), "");
        }
        if (session.state() != SessionState.IN_ROUND || session.roundState() != RoundState.ACTIVE) {
            return Guidance.NONE;
        }
        if (!session.alivePlayers().contains(player.getUniqueId())) {
            return new Guidance("", plugin.messages().text("scoreboard.next-actions.wait-next-round", "翌日を待つ"));
        }
        if (!isInHomeArea(session, player)) {
            if (plugin.roundTimeService().remainingTicks(session) <= 3_000L) {
                return new Guidance(
                        plugin.messages().format(
                                "scoreboard.goals.return-before-midnight",
                                "{deadline}までに帰還",
                                "deadline", RoundClock.format(plugin.settings().roundTiming().deadlineTimeTicks())
                        ),
                        plugin.messages().text("scoreboard.next-actions.find-return", "帰還ポータルを探す")
                );
            }
            return new Guidance(
                    quotaGoal(delivered, quota),
                    plugin.messages().text("scoreboard.next-actions.collect-treasure", "宝を集めて帰還")
            );
        }
        if (!session.hasExploredThisRound(player.getUniqueId()) && delivered < quota) {
            return new Guidance(
                    quotaGoal(delivered, quota),
                    plugin.messages().text("scoreboard.next-actions.go-depart", "出発地点からダンジョンへ")
            );
        }
        long missing = Math.max(0L, quota - delivered);
        if (missing > 0L) {
            return new Guidance(
                    quotaGoal(delivered, quota),
                    hasDeliverable(player)
                            ? plugin.messages().text("scoreboard.next-actions.deliver-treasure", "宝を納品する")
                            : plugin.messages().text("scoreboard.next-actions.dive-again-or-sleep", "再出発かベッドで休む")
            );
        }
        return new Guidance(
                plugin.messages().text("scoreboard.goals.quota-achieved", "ノルマ達成"),
                player.isSleeping() ? "" : plugin.messages().text("scoreboard.next-actions.sleep", "ベッドで休む")
        );
    }

    private String quotaGoal(long delivered, long quota) {
        return delivered >= quota
                ? plugin.messages().text("scoreboard.goals.quota-achieved", "ノルマ達成")
                : plugin.messages().format("scoreboard.goals.quota-needed", "ノルマまであと {amount}", "amount", quota - delivered);
    }

    private boolean hasDeliverable(Player player) {
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null && plugin.settings().economy().sellPrices().containsKey(item.getType())) {
                return true;
            }
        }
        return false;
    }

    private record Guidance(String goal, String next) {
        private static final Guidance NONE = new Guidance("", "");
    }
}

final class AngerGauge {
    private static final int LENGTH = 19;

    private AngerGauge() {
    }

    static String render(int misses, int maximum) {
        int safeMaximum = Math.max(1, maximum);
        int angry = Math.clamp(misses, 0, safeMaximum);
        if (angry > 0 && angry >= safeMaximum - 1) {
            return "§c" + "|".repeat(LENGTH);
        }
        int filled = angry == 0 ? Math.ceilDiv(LENGTH, 3) : Math.ceilDiv(LENGTH * 2, 3);
        String color = angry == 0 ? "§a" : "§6";
        return color + "|".repeat(filled) + "§7" + "|".repeat(LENGTH - filled);
    }
}
