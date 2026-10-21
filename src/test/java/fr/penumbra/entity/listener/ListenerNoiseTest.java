package fr.penumbra.entity.listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Invariants du barème de bruit de L'Écouteur. Le contrat central — <b>immobile
 * = inaudible, à n'importe quelle distance</b> — est la promesse faite au joueur :
 * il est testé explicitement, y compris au contact.
 */
class ListenerNoiseTest {

    @Test
    void perfectStillnessIsAlwaysInaudibleEvenAtPointBlank() {
        double still = ListenerNoise.movementRadius(0.0, false, false);
        assertEquals(0.0, still);
        // Un bruit de portée nulle n'est jamais entendu, même collé à l'oreille.
        for (double distance = 0.0; distance <= 40.0; distance += 0.5) {
            assertFalse(ListenerNoise.audible(still, distance),
                    "portée nulle audible à " + distance);
        }
        // Sprinter sur place n'existe pas : c'est la vitesse qui décide, pas le drapeau.
        assertEquals(0.0, ListenerNoise.movementRadius(0.0, true, false));
        assertEquals(0.0, ListenerNoise.movementRadius(ListenerNoise.STILL_SPEED, true, false));
    }

    @Test
    void movementRadiusOrdersStillnessCrouchWalkAndSprint() {
        double crouch = ListenerNoise.movementRadius(0.09, false, true);
        double walk = ListenerNoise.movementRadius(0.09, false, false);
        double sprint = ListenerNoise.movementRadius(0.20, true, false);
        assertTrue(crouch < walk, "accroupi doit être plus discret que la marche");
        assertTrue(walk < sprint, "la marche doit être plus discrète que le sprint");
        assertEquals(ListenerNoise.CROUCH_WALK_RADIUS, crouch);
        assertEquals(ListenerNoise.WALK_RADIUS, walk);
        assertEquals(ListenerNoise.SPRINT_RADIUS, sprint);
        // L'accroupissement prime sur le sprint : sprinter accroupi reste discret.
        assertEquals(ListenerNoise.CROUCH_WALK_RADIUS,
                ListenerNoise.movementRadius(0.20, true, true));
    }

    @Test
    void swingingIsHeardEvenFromAPerfectlyStillPlayer() {
        double radius = ListenerNoise.activityRadius(0.0, true, false, false);
        assertEquals(ListenerNoise.SWING_BONUS, radius);
        assertTrue(ListenerNoise.audible(radius, ListenerNoise.SWING_BONUS - 0.1));
        assertFalse(ListenerNoise.audible(radius, ListenerNoise.SWING_BONUS + 0.1));
    }

    @Test
    void activityRadiusAccumulatesButNeverExceedsTheCeiling() {
        double loudest = ListenerNoise.activityRadius(
                ListenerNoise.SPRINT_RADIUS, true, true, true);
        assertEquals(ListenerNoise.MAX_RADIUS, loudest);
        assertTrue(loudest <= ListenerNoise.MAX_RADIUS);
        // Un mouvement négatif (valeur aberrante) ne crée jamais de portée négative.
        assertEquals(0.0, ListenerNoise.activityRadius(-50.0, false, false, false));
    }

    @Test
    void lockingRequiresBothAFrankNoiseAndCloseRange() {
        // Assez fort, assez près : verrouillage.
        assertTrue(ListenerNoise.lockable(ListenerNoise.WALK_RADIUS, 3.0));
        // Assez fort mais trop loin.
        assertFalse(ListenerNoise.lockable(ListenerNoise.SPRINT_RADIUS,
                ListenerNoise.LOCK_DISTANCE + 0.1));
        // Assez près mais trop discret : se déplacer accroupi à côté d'elle est sûr.
        assertFalse(ListenerNoise.lockable(ListenerNoise.CROUCH_WALK_RADIUS, 0.5));
        // Immobile et collé : jamais.
        assertFalse(ListenerNoise.lockable(0.0, 0.0));
        // Exactement à la limite : inclusif, pour rester prévisible.
        assertTrue(ListenerNoise.lockable(ListenerNoise.LOCK_MIN_RADIUS,
                ListenerNoise.LOCK_DISTANCE));
    }

    @Test
    void listenDurationStaysWithinItsBounds() {
        assertEquals(ListenerNoise.LISTEN_MIN_TICKS, ListenerNoise.listenDuration(0));
        for (int entropy = -400; entropy <= 400; entropy++) {
            int duration = ListenerNoise.listenDuration(entropy);
            assertTrue(duration >= ListenerNoise.LISTEN_MIN_TICKS
                            && duration <= ListenerNoise.LISTEN_MAX_TICKS,
                    "durée hors bornes : " + duration);
        }
    }

    @Test
    void aimErrorIsBoundedAndCentredOnZero() {
        assertEquals(-ListenerNoise.AIM_ERROR, ListenerNoise.aimOffset(0.0));
        assertEquals(ListenerNoise.AIM_ERROR, ListenerNoise.aimOffset(1.0));
        assertEquals(0.0, ListenerNoise.aimOffset(0.5));
        // Un tirage hors [0,1] est borné, jamais amplifié.
        assertEquals(-ListenerNoise.AIM_ERROR, ListenerNoise.aimOffset(-9.0));
        assertEquals(ListenerNoise.AIM_ERROR, ListenerNoise.aimOffset(9.0));
        // Elle ne connaît jamais la position exacte à mieux qu'un bloc et demi.
        assertTrue(Math.abs(ListenerNoise.aimOffset(0.0)) <= ListenerNoise.AIM_ERROR);
    }

    @Test
    void cooldownHandlesBoundaryAndWorldTimeRollback() {
        long cooldown = ListenerManager.PLAYER_COOLDOWN_TICKS;
        assertTrue(ListenerNoise.cooldownReady(null, 500L, cooldown));
        assertFalse(ListenerNoise.cooldownReady(500L, 500L + cooldown - 1L, cooldown));
        assertTrue(ListenerNoise.cooldownReady(500L, 500L + cooldown, cooldown));
        // Retour en arrière de l'horloge du monde : on ne bloque pas indéfiniment.
        assertTrue(ListenerNoise.cooldownReady(500L, 10L, cooldown));
    }

    @Test
    void frequencyPressureScalesAndNeverCreatesInvalidRandomBound() {
        assertEquals(46, ListenerNoise.spawnDenominator(46, 1.0f, 1.0f));
        assertEquals(12, ListenerNoise.spawnDenominator(46, 2.0f, 2.0f));
        assertEquals(1, ListenerNoise.spawnDenominator(46, 100.0f, 100.0f));
        // Aucun dénominateur nul ou négatif, quelles que soient les entrées.
        for (float frequency : new float[] {0.0f, 0.001f, 1.0f, 4.0f}) {
            for (float intensity : new float[] {0.0f, 0.001f, 1.0f, 2.0f}) {
                assertTrue(ListenerNoise.spawnDenominator(46, frequency, intensity) >= 1);
            }
        }
    }

    @Test
    void behaviorIdsAreDefensiveAgainstCorruptedSyncedValues() {
        assertEquals(ListenerBehavior.SEEKING, ListenerBehavior.byId(0));
        assertEquals(ListenerBehavior.LEAVING,
                ListenerBehavior.byId(ListenerBehavior.values().length - 1));
        assertEquals(ListenerBehavior.SEEKING, ListenerBehavior.byId(-1));
        assertEquals(ListenerBehavior.SEEKING, ListenerBehavior.byId(999));
    }
}
