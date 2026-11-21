package net.azisaba.aziCave.achievement;

import net.azisaba.aziCave.statistics.PlayerStatistics;

import java.util.function.ToLongFunction;

/**
 * Maps AziCave statistics to AzisabaAchievements progress keys.
 * Progress that is not derived from statistics (such as boss defeats) is stored by AzisabaAchievements itself.
 */
public enum AchievementProgress {
    MAX_ROUND("azisaba:azicave/max-round", PlayerStatistics::maxRound),
    TOTAL_ROUNDS_REACHED("azisaba:azicave/total-rounds-reached", PlayerStatistics::totalRoundsReached),
    SESSIONS_JOINED("azisaba:azicave/sessions-joined", PlayerStatistics::sessionsJoined),
    MAX_DEPTH("azisaba:azicave/max-depth", PlayerStatistics::maxDepth),
    TOTAL_PLAY_HOURS("azisaba:azicave/total-play-hours", statistics -> toHours(statistics.totalPlaySeconds())),
    LONGEST_PLAY_HOURS("azisaba:azicave/longest-play-hours", statistics -> toHours(statistics.longestPlaySeconds())),
    MOB_KILLS("azisaba:azicave/mob-kills", PlayerStatistics::mobKills),
    TOTAL_SALES("azisaba:azicave/total-sales", PlayerStatistics::totalSales);

    private final String key;
    private final ToLongFunction<PlayerStatistics> extractor;

    AchievementProgress(String key, ToLongFunction<PlayerStatistics> extractor) {
        this.key = key;
        this.extractor = extractor;
    }

    public String key() {
        return key;
    }

    public long valueOf(PlayerStatistics statistics) {
        return Math.max(0L, extractor.applyAsLong(statistics));
    }

    private static long toHours(long seconds) {
        return seconds / 3_600L;
    }
}
