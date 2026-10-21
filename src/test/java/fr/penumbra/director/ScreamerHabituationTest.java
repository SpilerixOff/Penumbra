package fr.penumbra.director;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreamerHabituationTest {

    private static final UUID PLAYER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @AfterEach
    void cleanup() {
        ScreamerHabituation.clearAll();
    }

    @Test
    void firstScreamerIsAlwaysFullStrength() {
        assertEquals(1.0f, ScreamerHabituation.intensityMultiplier(0));
        assertEquals(0.0f, ScreamerHabituation.denialChance(0, false));
        ScreamerHabituation.Decision decision =
                ScreamerHabituation.evaluate(0, Long.MIN_VALUE, 1000L, false, 0.0f);
        assertFalse(decision.deny());
        assertEquals(1.0f, decision.intensityMultiplier());
    }

    @Test
    void intensityDecaysWithExposureButNeverBelowFloor() {
        float previous = 2.0f;
        for (int recent = 0; recent <= 12; recent++) {
            float multiplier = ScreamerHabituation.intensityMultiplier(recent);
            assertTrue(multiplier <= previous, "la décote doit être monotone");
            assertTrue(multiplier >= ScreamerHabituation.FLOOR);
            previous = multiplier;
        }
        assertEquals(ScreamerHabituation.FLOOR, ScreamerHabituation.intensityMultiplier(12));
    }

    @Test
    void denialGrowsWithExposureAndIsCapped() {
        assertEquals(0.0f, ScreamerHabituation.denialChance(1, false));
        assertTrue(ScreamerHabituation.denialChance(2, false) > 0.0f);
        assertTrue(ScreamerHabituation.denialChance(3, false)
                > ScreamerHabituation.denialChance(2, false));
        assertEquals(ScreamerHabituation.DENIAL_CAP,
                ScreamerHabituation.denialChance(50, false));
    }

    @Test
    void contactFinalsAreNeverDenied() {
        assertEquals(0.0f, ScreamerHabituation.denialChance(50, true));
        assertFalse(ScreamerHabituation.tooSoon(999L, 1000L, true));
        // Même un tirage qui refuserait un screamer normal laisse passer le contact.
        ScreamerHabituation.Decision decision =
                ScreamerHabituation.evaluate(6, 999L, 1000L, true, 0.0f);
        assertFalse(decision.deny());
        assertTrue(decision.intensityMultiplier() < 1.0f);
    }

    @Test
    void nonContactFacesRespectMinimumGap() {
        long last = 10_000L;
        assertTrue(ScreamerHabituation.tooSoon(last,
                last + ScreamerHabituation.MIN_FACE_GAP_TICKS - 1, false));
        assertFalse(ScreamerHabituation.tooSoon(last,
                last + ScreamerHabituation.MIN_FACE_GAP_TICKS, false));
        assertFalse(ScreamerHabituation.tooSoon(Long.MIN_VALUE, 0L, false));
        ScreamerHabituation.Decision decision = ScreamerHabituation.evaluate(
                1, last, last + 100L, false, 0.99f);
        assertTrue(decision.deny(), "un visage à 5 s du précédent doit être refusé");
    }

    @Test
    void sessionMemoryDecaysCountsOutsideWindow() {
        RandomSource random = RandomSource.create(12345L);
        ScreamerHabituation.recordFace(PLAYER, 1000L);
        ScreamerHabituation.recordFace(PLAYER, 2000L);
        ScreamerHabituation.recordFace(PLAYER, 3000L);

        // Bien après la fenêtre : l'historique expiré ne pèse plus rien.
        long later = 3000L + ScreamerHabituation.RECENT_WINDOW_TICKS + 10_000L;
        ScreamerHabituation.Decision decision =
                ScreamerHabituation.decide(PLAYER, later, false, random);
        assertFalse(decision.deny());
        assertEquals(1.0f, decision.intensityMultiplier());
    }

    @Test
    void clockGoingBackwardsClearsHistoryInsteadOfBlocking() {
        RandomSource random = RandomSource.create(54321L);
        ScreamerHabituation.recordFace(PLAYER, 500_000L);
        // Nouveau monde, horloge repartie à zéro : pas de faux « trop tôt ».
        ScreamerHabituation.Decision decision =
                ScreamerHabituation.decide(PLAYER, 100L, false, random);
        assertFalse(decision.deny());
        assertEquals(1.0f, decision.intensityMultiplier());
    }

    @Test
    void forgetDropsPlayerState() {
        ScreamerHabituation.recordFace(PLAYER, 1000L);
        ScreamerHabituation.recordFace(PLAYER, 1600L);
        ScreamerHabituation.forget(PLAYER);
        RandomSource random = RandomSource.create(7L);
        ScreamerHabituation.Decision decision =
                ScreamerHabituation.decide(PLAYER, 1700L, false, random);
        assertFalse(decision.deny());
        assertEquals(1.0f, decision.intensityMultiplier());
    }

    @Test
    void historyIsBoundedPerPlayer() {
        for (int i = 0; i < 40; i++) {
            ScreamerHabituation.recordFace(PLAYER, 1000L + i);
        }
        // Même saturé, le multiplicateur reste au plancher, jamais en dessous.
        RandomSource random = RandomSource.create(9L);
        ScreamerHabituation.Decision decision = ScreamerHabituation.decide(
                PLAYER, 1000L + 40 + ScreamerHabituation.MIN_FACE_GAP_TICKS, false, random);
        if (!decision.deny()) {
            assertTrue(decision.intensityMultiplier() >= ScreamerHabituation.FLOOR);
            assertTrue(decision.intensityMultiplier()
                    <= ScreamerHabituation.intensityMultiplier(ScreamerHabituation.HISTORY_LIMIT));
        }
    }
}
