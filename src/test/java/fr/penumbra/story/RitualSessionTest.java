package fr.penumbra.story;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RitualSessionTest {
    private final Object player = new Object();
    private final Object level = new Object();

    @Test
    void normalPreludeSurvivesUntilItsDeadline() {
        RitualSession session = new RitualSession(player, level, 100);
        assertTrue(session.validate(player, level, true, 1600, 360));
        assertTrue(session.validate(player, level, true, 0, 399));
        assertFalse(session.validate(player, level, true, 0, 400));
    }

    @Test
    void reconnectOrRespawnCannotResumeAnOldPlayersClosures() {
        RitualSession session = new RitualSession(player, level, 100);
        assertFalse(session.validate(new Object(), level, true, 0, 110));
        assertFalse(session.validate(player, level, true, 0, 111));
    }

    @Test
    void returningAfterDimensionChangeOrLeavingArenaDoesNotRevivePrelude() {
        RitualSession dimension = new RitualSession(player, level, 100);
        assertFalse(dimension.validate(player, new Object(), true, 0, 110));
        assertFalse(dimension.validate(player, level, true, 0, 111));
        RitualSession distance = new RitualSession(player, level, 100);
        assertFalse(distance.validate(player, level, true, 1600.01, 110));
        assertFalse(distance.validate(player, level, true, 0, 111));
    }

    @Test
    void deathSleepOrSpectatorInvalidationIsFinalForThisAttempt() {
        RitualSession session = new RitualSession(player, level, 100);
        assertFalse(session.validate(player, level, false, 0, 110));
        assertFalse(session.validate(player, level, true, 0, 111));
    }

    @Test
    void cancellingOneAttemptDoesNotCancelAnotherPlayersSessionOrARetry() {
        RitualSession previous = new RitualSession(player, level, 100);
        Object otherPlayer = new Object();
        RitualSession other = new RitualSession(otherPlayer, level, 100);
        previous.cancel();
        RitualSession retry = new RitualSession(player, level, 110);
        assertFalse(previous.validate(player, level, true, 0, 111));
        assertTrue(other.validate(otherPlayer, level, true, 0, 111));
        assertTrue(retry.validate(player, level, true, 0, 111));
    }
}
