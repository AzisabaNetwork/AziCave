package net.azisaba.aziCave.game;

import net.azisaba.aziCave.config.GuiSettings;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DepartureGuardTest {
    @Test
    void onlyIdleLobbyMayBePreparedManually() {
        assertTrue(DepartureGuard.canPrepare(SessionState.LOBBY, RoundState.ENDED));
        for (SessionState state : SessionState.values()) {
            assertFalse(DepartureGuard.canPrepare(state, RoundState.PREPARING));
            assertFalse(DepartureGuard.canPrepare(state, RoundState.ENDING));
        }
        for (SessionState state : new SessionState[]{SessionState.IN_ROUND, SessionState.GAME_OVER, SessionState.CLOSING}) {
            for (RoundState round : RoundState.values()) assertFalse(DepartureGuard.canPrepare(state, round));
        }
    }

    @Test
    void anEndedRoundMustReturnToTheLobbyForDepthSelection() {
        assertTrue(DepartureGuard.canStartRound(SessionState.LOBBY, RoundState.ENDED));
        assertFalse(DepartureGuard.canStartRound(SessionState.IN_ROUND, RoundState.ENDED));
        assertFalse(DepartureGuard.canStartRound(SessionState.IN_ROUND, RoundState.ACTIVE));
    }

    @Test
    void onlyDayZeroAdvancesWhenStartingADay() {
        assertEquals(1, DepartureGuard.dayToStart(0));
        assertEquals(2, DepartureGuard.dayToStart(2));
    }

    @Test
    void lateJoinerAtTheEntranceKeepsTheirPosition() {
        assertFalse(DepartureGuard.needsHomeTeleport(false, true));
        assertTrue(DepartureGuard.needsHomeTeleport(true, true));
        assertTrue(DepartureGuard.needsHomeTeleport(false, false));
    }

    @Test
    void dialogDepthAcceptsOnlyConfiguredDangerLevels() {
        List<GuiSettings.DangerLevel> levels = List.of(new GuiSettings.DangerLevel("超安全！", 3), new GuiSettings.DangerLevel("超危険！", 12));
        for (String option : new String[]{null, "", "1", "7", "3.0", "abc", "超安全！"}) {
            assertTrue(DepartureGuard.selectedDepth(option, levels).isEmpty());
        }
        assertEquals(3, DepartureGuard.selectedDepth("3", levels).getAsInt());
        assertEquals(12, DepartureGuard.selectedDepth("12", levels).getAsInt());
    }

    @Test
    void unknownDepthFallsBackToNumber() {
        GuiSettings settings = new GuiSettings(List.of(new GuiSettings.DangerLevel("普通", 7)), 7);
        assertEquals("普通", settings.describe(7));
        assertEquals("5", settings.describe(5));
    }
}
