package fr.penumbra.event.personal;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MultiplayerSceneRulesTest {

    @Test
    void companionMustBeAliveInTheSameDimensionAndInsideTheExactBounds() {
        assertTrue(MultiplayerSceneRules.eligibleCompanion(
                false, true, true, false, 3.0, 3.0, 28.0));
        assertTrue(MultiplayerSceneRules.eligibleCompanion(
                false, true, true, false, 28.0, 3.0, 28.0));

        assertFalse(MultiplayerSceneRules.eligibleCompanion(
                true, true, true, false, 8.0, 3.0, 28.0));
        assertFalse(MultiplayerSceneRules.eligibleCompanion(
                false, false, true, false, 8.0, 3.0, 28.0));
        assertFalse(MultiplayerSceneRules.eligibleCompanion(
                false, true, false, false, 8.0, 3.0, 28.0));
        assertFalse(MultiplayerSceneRules.eligibleCompanion(
                false, true, true, true, 8.0, 3.0, 28.0));
        assertFalse(MultiplayerSceneRules.eligibleCompanion(
                false, true, true, false, 28.01, 3.0, 28.0));
        assertFalse(MultiplayerSceneRules.eligibleCompanion(
                false, true, true, false, Double.NaN, 3.0, 28.0));
    }

    @Test
    void aConnectedCompanionNameAlwaysWinsOverTheSoloAlias() {
        List<String> companions = List.of("Alice", "Bastien", "Chloe");

        assertEquals("Alice", MultiplayerSceneRules.chooseImitatedName(
                "Temoin", companions, 0));
        assertEquals("Chloe", MultiplayerSceneRules.chooseImitatedName(
                "Temoin", companions, -1));
    }

    @Test
    void anOnlinePlayerAlwaysBeatsTheOwnerEvenWhenFarAway() {
        assertEquals(-1, MultiplayerSceneRules.imitationSourcePriority(
                true, true, true, false, 0.0, true));
        assertEquals(2, MultiplayerSceneRules.imitationSourcePriority(
                false, true, true, false, 180.0, false));
        assertEquals(1, MultiplayerSceneRules.imitationSourcePriority(
                false, true, true, false, 8.0, true));
        assertEquals(0, MultiplayerSceneRules.imitationSourcePriority(
                false, false, true, false, Double.POSITIVE_INFINITY, false));
        assertEquals(-1, MultiplayerSceneRules.imitationSourcePriority(
                false, true, false, false, 20.0, false));
        assertEquals(-1, MultiplayerSceneRules.imitationSourcePriority(
                false, true, true, true, 20.0, false));
    }

    @Test
    void soloAliasesRemainDeterministicAndNeverEmpty() {
        assertEquals("Alex?", MultiplayerSceneRules.chooseImitatedName(
                "Alex", List.of(), 0));
        assertEquals("_Alex_", MultiplayerSceneRules.chooseImitatedName(
                "Alex", List.of(), 1));
        assertEquals("xelA", MultiplayerSceneRules.chooseImitatedName(
                "Alex", List.of(), 2));
        assertEquals("Ale.", MultiplayerSceneRules.chooseImitatedName(
                "Alex", List.of(), 3));
        assertEquals("Absent", MultiplayerSceneRules.chooseImitatedName(
                "", List.of(), 3));
    }

    @Test
    void allThreeAsymmetricScenesAreRegisteredInThePersonalPool() {
        List<String> ids = PersonalEvents.create().stream()
                .map(event -> event.id())
                .toList();

        assertTrue(ids.contains("behind_other"));
        assertTrue(ids.contains("paired_echo"));
        assertTrue(ids.contains("stolen_name"));
        assertEquals(ids.size(), ids.stream().distinct().count());
    }
}
