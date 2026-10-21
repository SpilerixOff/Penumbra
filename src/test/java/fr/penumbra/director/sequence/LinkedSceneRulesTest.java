package fr.penumbra.director.sequence;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LinkedSceneRulesTest {

    @Test
    void caveChainsRequireUndergroundWhileNegativeWitnessDoesNot() {
        assertFalse(eligible(LinkedSceneType.FALSE_GUIDE, 6, false));
        assertFalse(eligible(LinkedSceneType.SKITTER_OMEN, 6, false));
        assertTrue(eligible(LinkedSceneType.NEGATIVE_WITNESS, 6, false));
        assertTrue(eligible(LinkedSceneType.FALSE_GUIDE, 6, true));
    }

    @Test
    void exactNarrativeStagesUnlockEachChain() {
        assertFalse(eligible(LinkedSceneType.SKITTER_OMEN, 1, true));
        assertTrue(eligible(LinkedSceneType.SKITTER_OMEN, 2, true));
        assertFalse(eligible(LinkedSceneType.FALSE_GUIDE, 2, true));
        assertTrue(eligible(LinkedSceneType.FALSE_GUIDE, 3, true));
        assertFalse(eligible(LinkedSceneType.NEGATIVE_WITNESS, 3, false));
        assertTrue(eligible(LinkedSceneType.NEGATIVE_WITNESS, 4, false));
    }

    @Test
    void everySafetyGateCanRejectTheScene() {
        assertFalse(LinkedSceneRules.eligible(LinkedSceneType.FALSE_GUIDE,
                false, true, false, false, true, true, 6, true));
        assertFalse(LinkedSceneRules.eligible(LinkedSceneType.FALSE_GUIDE,
                true, false, false, false, true, true, 6, true));
        assertFalse(LinkedSceneRules.eligible(LinkedSceneType.FALSE_GUIDE,
                true, true, true, false, true, true, 6, true));
        assertFalse(LinkedSceneRules.eligible(LinkedSceneType.FALSE_GUIDE,
                true, true, false, true, true, true, 6, true));
        assertFalse(LinkedSceneRules.eligible(LinkedSceneType.FALSE_GUIDE,
                true, true, false, false, false, true, 6, true));
        assertFalse(LinkedSceneRules.eligible(LinkedSceneType.FALSE_GUIDE,
                true, true, false, false, true, false, 6, true));
    }

    @Test
    void knownCounterplayAlwaysProvidesMoreWarningOrLessExposure() {
        assertTrue(LinkedSceneRules.followUpDelayTicks(
                LinkedSceneType.FALSE_GUIDE, true)
                > LinkedSceneRules.followUpDelayTicks(
                LinkedSceneType.FALSE_GUIDE, false));
        assertTrue(LinkedSceneRules.followUpDelayTicks(
                LinkedSceneType.SKITTER_OMEN, true)
                > LinkedSceneRules.followUpDelayTicks(
                LinkedSceneType.SKITTER_OMEN, false));
        assertTrue(LinkedSceneRules.cameraTrapTicks(true)
                < LinkedSceneRules.cameraTrapTicks(false));
    }

    private static boolean eligible(LinkedSceneType type, int stage, boolean underground) {
        return LinkedSceneRules.eligible(type, true, true, false,
                false, true, true, stage, underground);
    }
}
