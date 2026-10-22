package fr.penumbra.director.encounter;

import org.junit.jupiter.api.Test;

import static fr.penumbra.director.encounter.EncounterSession.Phase.*;
import fr.penumbra.director.encounter.EncounterSession.Variant;
import static org.junit.jupiter.api.Assertions.*;

class EncounterSessionTest {
    @Test
    void frozenClockCannotMoveTheActorButRollbackStillNeedsCancellation() {
        EncounterSession scene = new EncounterSession(Variant.RECOIL, 0);
        assertFalse(scene.clockChanged(0));
        assertTrue(scene.clockChanged(24));
        scene.advance(24, true, true, true);
        assertEquals(RETREAT, scene.phase());
        assertFalse(scene.clockChanged(24), "The physical retreat must not run during tick freeze");
        assertTrue(scene.clockChanged(23));
        assertEquals(DONE, scene.advance(23, true, true, true));
    }

    @Test
    void chargeRequiresTimeToSeeBodyAndContinuousObservation() {
        EncounterSession scene = new EncounterSession(Variant.LUNGE, 0);
        assertEquals(OBSERVE, scene.advance(9, true, true, true));
        assertEquals(OBSERVE, scene.advance(24, false, true, true));
        assertEquals(OBSERVE, scene.advance(33, true, true, true));
        assertEquals(LUNGE, scene.advance(34, true, true, true));
        assertEquals(LUNGE, scene.advance(95, true, true, true), "Leave time for the real recoil");
        assertEquals(DONE, scene.advance(106, true, true, true));
    }

    @Test
    void unseenActorRetreatsWithoutChargingOrRebounding() {
        for (var variant : EncounterSession.Variant.values()) {
            EncounterSession scene = new EncounterSession(variant, 0);
            assertEquals(RETREAT, scene.advance(100, false, true, true));
            assertEquals(DONE, scene.advance(132, false, true, true));
        }
    }

    @Test
    void disablingChasesKeepsTheBodyButPreventsAttackAndStopsAnActiveChase() {
        for (var variant : new EncounterSession.Variant[]{Variant.CHASE, Variant.LUNGE}) {
            EncounterSession scene = new EncounterSession(variant, 0);
            assertEquals(RETREAT, scene.advance(24, true, true, false));
        }
        EncounterSession scene = new EncounterSession(Variant.CHASE, 0);
        assertEquals(EncounterSession.Phase.CHASE, scene.advance(24, true, true, true));
        assertEquals(RETREAT, scene.advance(25, true, true, false));
        EncounterSession lunge = new EncounterSession(EncounterSession.Variant.LUNGE, 0);
        lunge.advance(24, true, true, true);
        assertEquals(DONE, lunge.advance(25, true, true, false));
    }

    @Test
    void witnessedRefugeReappearsOnlyOnceAndCanHaveNoActorDuringItsSilentBeat() {
        EncounterSession scene = new EncounterSession(Variant.REBOUND, 0);
        assertEquals(RETREAT, scene.advance(24, true, true, true));
        assertEquals(HUSH, scene.advance(56, true, true, true));
        assertEquals(HUSH, scene.advance(73, false, false, true));
        assertEquals(REAPPEAR, scene.advance(74, false, false, true));
        assertEquals(RETREAT, scene.advance(98, true, true, true));
        assertEquals(DONE, scene.advance(130, true, true, true));
    }

    @Test
    void cameraRecoilCancelsEveryPendingReboundAndNeverChargesAgain() {
        EncounterSession scene = new EncounterSession(Variant.REBOUND, 0);
        scene.advance(24, true, true, true);
        scene.recover();
        assertEquals(RECOVERY, scene.advance(59, true, true, true));
        assertEquals(DONE, scene.advance(60, true, false, true));
        assertEquals(DONE, scene.advance(70, true, true, true));
    }

    @Test
    void missingActorCancellationRollbackAndDeadlineCannotLeaveAReservedScene() {
        EncounterSession missing = new EncounterSession(Variant.RECOIL, 0);
        assertEquals(DONE, missing.advance(1, false, false, true));
        EncounterSession rollback = new EncounterSession(Variant.RECOIL, 40);
        assertEquals(DONE, rollback.advance(39, true, true, true));
        EncounterSession deadline = new EncounterSession(Variant.REBOUND, 0);
        assertEquals(DONE, deadline.advance(EncounterSession.MAX_TICKS, true, true, true));
        EncounterSession menu = new EncounterSession(EncounterSession.Variant.LUNGE, 0);
        menu.advance(24, true, true, true);
        menu.cancel();
        assertEquals(DONE, menu.advance(25, true, true, true));
    }

    @Test
    void shortPursuitHasAFixedFourSecondUpperBound() {
        EncounterSession scene = new EncounterSession(EncounterSession.Variant.CHASE, 0);
        scene.advance(24, true, true, true);
        assertEquals(EncounterSession.Phase.CHASE, scene.advance(103, true, true, true));
        assertEquals(RETREAT, scene.advance(104, true, true, true));
        assertEquals(DONE, scene.advance(136, true, true, true));
    }
}
