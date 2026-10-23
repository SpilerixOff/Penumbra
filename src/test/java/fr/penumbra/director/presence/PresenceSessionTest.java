package fr.penumbra.director.presence;

import org.junit.jupiter.api.Test;

import static fr.penumbra.director.presence.PresenceSession.Phase.*;
import static org.junit.jupiter.api.Assertions.*;

class PresenceSessionTest {
    @Test
    void listeningMakesRoomForTheCueBeforeAnyApproach() {
        PresenceSession scene = new PresenceSession(0);
        assertEquals(LISTENING, scene.advance(89, false, true, false, false, true, false));
        assertEquals(FOLLOWING, scene.advance(90, false, true, false, false, true, false));
    }

    @Test
    void lookingAloneGivesAVisiblePauseAndWithdrawalButNeverAnAttack() {
        PresenceSession scene = new PresenceSession(0);
        assertEquals(NOTICED, scene.advance(110, true, false, false, false, true, false));
        assertTrue(scene.witnessed());
        assertEquals(NOTICED, scene.advance(141, true, false, false, false, true, false));
        assertEquals(WITHDRAWING, scene.advance(142, true, false, false, false, true, false));
        assertEquals(WITHDRAWING, scene.advance(189, true, false, false, false, true, false));
        assertEquals(DEPARTING, scene.advance(190, true, false, false, false, true, false));
        assertEquals(DONE, scene.advance(270, true, false, false, false, true, false));
    }

    @Test
    void provocationNeedsASightingThenRetreatAndLiveLineOfSight() {
        PresenceSession scene = noticed();
        assertEquals(WITHDRAWING, scene.advance(142, true, false, false, true, true, false));
        assertEquals(LUNGE, scene.advance(190, true, false, false, true, true, false));
        PresenceSession behindWall = noticed();
        behindWall.advance(142, true, false, false, true, true, false);
        assertEquals(DEPARTING, behindWall.advance(190, false, true, false, true, true, false));
    }

    @Test
    void disablingAttacksAndRetreatOnlyVariantsNeverCharge() {
        PresenceSession scene = noticed();
        scene.advance(142, true, false, false, true, false, false);
        assertEquals(DEPARTING, scene.advance(190, true, false, false, true, false, false));
        PresenceSession attack = noticed();
        attack.advance(142, true, false, false, true, true, false);
        attack.advance(190, true, false, false, true, true, false);
        assertEquals(DONE, attack.advance(191, true, false, false, true, false, false));
    }

    @Test
    void fiveSecondsOfActualCalmBehindCoverEndsTheThreat() {
        PresenceSession scene = new PresenceSession(0);
        scene.advance(30, false, true, false, false, true, false);
        assertEquals(FOLLOWING, scene.advance(129, false, true, true, false, true, false));
        assertEquals(DEPARTING, scene.advance(130, false, true, true, false, true, false));
        assertEquals(DONE, scene.advance(210, false, true, true, false, true, false));
    }

    @Test
    void LookingAtTheBodyDoesNotCountAsConcealmentAndMovementResetsCalm() {
        PresenceSession visible = noticed();
        assertEquals(WITHDRAWING, visible.advance(210, true, false, true, false, true, false));
        PresenceSession moving = new PresenceSession(0);
        moving.advance(90, false, true, true, false, true, false);
        moving.advance(100, false, true, false, false, true, false);
        assertEquals(FOLLOWING, moving.advance(199, false, true, true, false, true, false));
        assertEquals(DEPARTING, moving.advance(200, false, true, true, false, true, false));
    }

    @Test
    void flashRecoveryCannotEverRearmTheAttack() {
        PresenceSession scene = noticed();
        scene.recover();
        assertEquals(RECOVERY, scene.advance(111, true, false, false, true, true, true));
        assertEquals(RECOVERY, scene.advance(154, true, false, false, true, true, true));
        assertEquals(DONE, scene.advance(155, true, false, false, true, true, true));
        assertEquals(DONE, scene.advance(156, true, false, false, true, true, false));
    }

    @Test
    void frozenClockRollbackAndHardDeadlineCannotLeaveALiveScene() {
        PresenceSession scene = new PresenceSession(50);
        assertFalse(scene.clockChanged(50));
        assertEquals(LISTENING, scene.advance(50, true, false, false, true, true, false));
        assertEquals(DONE, scene.advance(49, true, false, false, true, true, false));
        PresenceSession longScene = new PresenceSession(0);
        assertEquals(DEPARTING, longScene.advance(680, false, true, false, false, true, false));
        assertEquals(DONE, longScene.advance(760, false, true, false, false, true, false));
    }

    private static PresenceSession noticed() {
        PresenceSession scene = new PresenceSession(0);
        scene.advance(110, true, false, false, false, true, false);
        return scene;
    }
}
