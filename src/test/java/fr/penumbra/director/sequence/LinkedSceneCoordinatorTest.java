package fr.penumbra.director.sequence;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LinkedSceneCoordinatorTest {

    private static final UUID OWNER = new UUID(0L, 1L);
    private static final UUID WITNESS = new UUID(0L, 2L);
    private static final UUID OTHER = new UUID(0L, 3L);

    @Test
    void oneOwnerAndEveryCapturedWitnessReceiveAnExclusiveLease() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        var first = coordinator.tryAcquire(request(OWNER, LinkedSceneType.FALSE_GUIDE,
                SceneAudience.local(OWNER, Set.of(WITNESS)), false), 100L);

        assertTrue(first.started());
        assertEquals(LinkedSceneCoordinator.StartFailure.OWNER_BUSY,
                coordinator.tryAcquire(request(OWNER, LinkedSceneType.SKITTER_OMEN,
                        SceneAudience.privateTo(OWNER), false), 101L).failure());
        assertEquals(LinkedSceneCoordinator.StartFailure.AUDIENCE_BUSY,
                coordinator.tryAcquire(request(OTHER, LinkedSceneType.NEGATIVE_WITNESS,
                        SceneAudience.local(OTHER, Set.of(WITNESS)), false), 101L).failure());
        assertTrue(coordinator.isOwnerBusy(OWNER));
        assertTrue(coordinator.isParticipantBusy(WITNESS));

        assertTrue(coordinator.cancel(first.token(), 102L));
        assertFalse(coordinator.isOwnerBusy(OWNER));
        assertFalse(coordinator.isParticipantBusy(WITNESS));
    }

    @Test
    void flashEndsFalseGuideBeforeItCanScheduleTheRampant() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        UUID token = coordinator.tryAcquire(request(OWNER, LinkedSceneType.FALSE_GUIDE,
                SceneAudience.privateTo(OWNER), false), 0L).token();

        assertEquals(LinkedSceneCoordinator.SignalResult.COUNTERED,
                coordinator.signal(token, LinkedSceneCoordinator.Signal.ABSENT_FLASHED, 20L));
        assertTrue(coordinator.pollDue(500L).isEmpty());
        assertEquals(LinkedSceneCoordinator.EndReason.COUNTERED_FLASH,
                coordinator.drainCompletions().getFirst().reason());
    }

    @Test
    void falseGuideDispatchesOneRampantAndNeverForcesAnUnloadedArea() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        UUID token = coordinator.tryAcquire(request(OWNER, LinkedSceneType.FALSE_GUIDE,
                SceneAudience.privateTo(OWNER), false), 1_000L).token();
        assertEquals(LinkedSceneCoordinator.SignalResult.UPDATED,
                coordinator.signal(token, LinkedSceneCoordinator.Signal.ABSENT_FOLLOWED,
                        1_010L));

        assertTrue(coordinator.pollDue(1_069L).isEmpty());
        List<LinkedSceneCoordinator.SceneAction> first = coordinator.pollDue(1_070L);
        assertEquals(1, first.size());
        assertEquals(LinkedSceneCoordinator.ActionType.SPAWN_RAMPANT,
                first.getFirst().action());
        assertEquals(1, first.getFirst().attempt());

        // L'integration a seulement consulte isLoaded : aucun chunk n'est cree.
        assertTrue(coordinator.acknowledge(token,
                LinkedSceneCoordinator.ActionOutcome.AREA_UNLOADED, 1_070L));
        assertTrue(coordinator.pollDue(1_089L).isEmpty());
        List<LinkedSceneCoordinator.SceneAction> retry = coordinator.pollDue(1_090L);
        assertEquals(2, retry.getFirst().attempt());
        assertTrue(coordinator.acknowledge(token,
                LinkedSceneCoordinator.ActionOutcome.AREA_UNLOADED, 1_090L));

        assertFalse(coordinator.isOwnerBusy(OWNER));
        assertEquals(LinkedSceneCoordinator.EndReason.FOLLOW_UP_UNAVAILABLE,
                coordinator.drainCompletions().getFirst().reason());
    }

    @Test
    void solvedSkitterClueProvidesTimeToLightTheCave() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        UUID token = coordinator.tryAcquire(request(OWNER, LinkedSceneType.SKITTER_OMEN,
                SceneAudience.privateTo(OWNER), true), 200L).token();
        coordinator.signal(token, LinkedSceneCoordinator.Signal.SCUTTLER_VANISHED, 220L);

        assertTrue(coordinator.pollDue(379L).isEmpty());
        assertEquals(LinkedSceneCoordinator.SignalResult.COUNTERED,
                coordinator.signal(token, LinkedSceneCoordinator.Signal.CAVE_LIT, 379L));
        assertTrue(coordinator.pollDue(500L).isEmpty());
        assertEquals(LinkedSceneCoordinator.EndReason.COUNTERED_LIGHT,
                coordinator.drainCompletions().getFirst().reason());
    }

    @Test
    void projectionistOnlyReleasesOriginelIfCameraIsReopenedInTime() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        UUID token = coordinator.tryAcquire(request(OWNER,
                LinkedSceneType.NEGATIVE_WITNESS,
                SceneAudience.privateTo(OWNER), false), 5_000L).token();
        coordinator.signal(token,
                LinkedSceneCoordinator.Signal.PROJECTIONIST_NATURAL_VANISH, 5_080L);

        assertTrue(coordinator.pollDue(5_100L).isEmpty());
        assertEquals(LinkedSceneCoordinator.SignalResult.UPDATED,
                coordinator.signal(token,
                        LinkedSceneCoordinator.Signal.CAMERA_REOPENED, 5_200L));
        List<LinkedSceneCoordinator.SceneAction> actions = coordinator.pollDue(5_200L);
        assertEquals(LinkedSceneCoordinator.ActionType.SPAWN_ORIGINEL,
                actions.getFirst().action());
        assertTrue(coordinator.acknowledge(token,
                LinkedSceneCoordinator.ActionOutcome.SPAWNED, 5_200L));
        assertEquals(LinkedSceneCoordinator.EndReason.COMPLETED,
                coordinator.drainCompletions().getFirst().reason());
    }

    @Test
    void keepingTheCameraClosedEscapesTheNegativeWitnessTrap() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        UUID token = coordinator.tryAcquire(request(OWNER,
                LinkedSceneType.NEGATIVE_WITNESS,
                SceneAudience.privateTo(OWNER), true), 10L).token();
        coordinator.signal(token,
                LinkedSceneCoordinator.Signal.PROJECTIONIST_NATURAL_VANISH, 50L);

        assertTrue(coordinator.pollDue(349L).isEmpty());
        assertTrue(coordinator.pollDue(350L).isEmpty());
        assertFalse(coordinator.isOwnerBusy(OWNER));
        assertEquals(LinkedSceneCoordinator.EndReason.ESCAPED_CAMERA_TRAP,
                coordinator.drainCompletions().getFirst().reason());
    }

    @Test
    void clockRollbackCannotLeaveAStaleLease() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator();
        UUID token = coordinator.tryAcquire(request(OWNER, LinkedSceneType.FALSE_GUIDE,
                SceneAudience.privateTo(OWNER), false), 10_000L).token();

        assertEquals(LinkedSceneCoordinator.SignalResult.EXPIRED,
                coordinator.signal(token, LinkedSceneCoordinator.Signal.ABSENT_FOLLOWED, 5L));
        assertFalse(coordinator.isOwnerBusy(OWNER));
        assertEquals(LinkedSceneCoordinator.EndReason.CLOCK_ROLLBACK,
                coordinator.drainCompletions().getFirst().reason());
    }

    @Test
    void globalCapacityIsStrictlyBounded() {
        LinkedSceneCoordinator coordinator = new LinkedSceneCoordinator(1);
        assertTrue(coordinator.tryAcquire(request(OWNER, LinkedSceneType.FALSE_GUIDE,
                SceneAudience.privateTo(OWNER), false), 0L).started());
        assertEquals(LinkedSceneCoordinator.StartFailure.CAPACITY_REACHED,
                coordinator.tryAcquire(request(OTHER, LinkedSceneType.NEGATIVE_WITNESS,
                        SceneAudience.privateTo(OTHER), false), 0L).failure());
    }

    private static LinkedSceneCoordinator.StartRequest request(
            UUID owner, LinkedSceneType type, SceneAudience audience,
            boolean counterplayKnown) {
        return new LinkedSceneCoordinator.StartRequest(owner, type, audience,
                "minecraft:overworld", new LinkedSceneCoordinator.SceneAnchor(0.5, 32.0, 0.5),
                counterplayKnown);
    }
}
