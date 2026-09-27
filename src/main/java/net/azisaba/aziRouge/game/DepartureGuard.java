package net.azisaba.aziRouge.game;

import net.azisaba.aziRouge.config.GuiSettings;

final class DepartureGuard {
    private DepartureGuard() {}

    static boolean canPrepare(SessionState state, RoundState round) {
        return state == SessionState.LOBBY && round == RoundState.ENDED;
    }

    static boolean canStartRound(SessionState state, RoundState round) {
        return canPrepare(state, round);
    }

    static int dayToStart(int currentDay) {
        return Math.max(1, currentDay);
    }

    static boolean needsHomeTeleport(boolean wasSpectating, boolean inHomeArea) {
        return wasSpectating || !inHomeArea;
    }

    static java.util.OptionalInt selectedDepth(String option, java.util.List<GuiSettings.DangerLevel> levels) {
        return levels.stream()
                .filter(level -> String.valueOf(level.depth()).equals(option))
                .mapToInt(GuiSettings.DangerLevel::depth)
                .findFirst();
    }
}
