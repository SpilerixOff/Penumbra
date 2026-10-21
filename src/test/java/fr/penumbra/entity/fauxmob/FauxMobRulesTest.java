package fr.penumbra.entity.fauxmob;

import fr.penumbra.entity.fauxmob.FauxMobRules.Defect;
import fr.penumbra.entity.fauxmob.FauxMobRules.Disguise;
import fr.penumbra.entity.fauxmob.FauxMobRules.RevealCause;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FauxMobRulesTest {

    @Test
    void disguiseCoversAllAnimalsAndRespectsZombieGate() {
        Set<Disguise> withoutZombie = EnumSet.noneOf(Disguise.class);
        Set<Disguise> withZombie = EnumSet.noneOf(Disguise.class);
        for (float roll = 0.0f; roll < 1.0f; roll += 0.01f) {
            withoutZombie.add(FauxMobRules.pickDisguise(roll, false));
            withZombie.add(FauxMobRules.pickDisguise(roll, true));
        }
        assertFalse(withoutZombie.contains(Disguise.ZOMBIE),
                "le zombie exige la nuit en surface");
        assertEquals(4, withoutZombie.size(), "les quatre animaux doivent sortir");
        assertEquals(5, withZombie.size(), "le zombie doit sortir la nuit");
        // Entrées dégénérées : jamais d'exception, retour stable.
        assertEquals(Disguise.COW, FauxMobRules.pickDisguise(Float.NaN, false));
    }

    @Test
    void perfectChanceGrowsWithExperienceAndIsCapped() {
        float previous = -1.0f;
        for (int seen = 0; seen <= 20; seen++) {
            float chance = FauxMobRules.perfectChance(seen);
            assertTrue(chance >= previous, "la part de parfaits doit croître");
            assertTrue(chance <= FauxMobRules.PERFECT_CAP);
            previous = chance;
        }
        assertEquals(FauxMobRules.PERFECT_BASE, FauxMobRules.perfectChance(0));
        assertEquals(FauxMobRules.PERFECT_CAP, FauxMobRules.perfectChance(50));
        assertEquals(FauxMobRules.PERFECT_BASE, FauxMobRules.perfectChance(-3));
    }

    @Test
    void defectSelectionYieldsPerfectAndAllThreeDefects() {
        // perfectRoll sous la base → parfait, quel que soit l'autre roll.
        assertEquals(Defect.NONE, FauxMobRules.pickDefect(0.9f, 0.0f, 0));
        // perfectRoll au-dessus → les trois défauts sortent selon roll.
        assertEquals(Defect.SILENT, FauxMobRules.pickDefect(0.1f, 0.99f, 0));
        assertEquals(Defect.STARE, FauxMobRules.pickDefect(0.5f, 0.99f, 0));
        assertEquals(Defect.FROZEN, FauxMobRules.pickDefect(0.9f, 0.99f, 0));
    }

    @Test
    void perfectCopiesNeverReactToStares() {
        assertFalse(FauxMobRules.stareCanReveal(Defect.NONE),
                "une copie parfaite ne réagit jamais au regard : c'est sa définition");
        assertTrue(FauxMobRules.stareCanReveal(Defect.SILENT));
        assertTrue(FauxMobRules.stareCanReveal(Defect.STARE));
        assertTrue(FauxMobRules.stareCanReveal(Defect.FROZEN));
    }

    @Test
    void staringRequiresTightConeAndRange() {
        double inRange = 10.0 * 10.0;
        assertTrue(FauxMobRules.isStaring(0.99, inRange));
        assertFalse(FauxMobRules.isStaring(0.9, inRange), "hors du cône");
        assertFalse(FauxMobRules.isStaring(0.99,
                (FauxMobRules.STARE_RANGE + 1) * (FauxMobRules.STARE_RANGE + 1)),
                "hors de portée");
    }

    @Test
    void timeoutOnlyHappensUnseen() {
        assertFalse(FauxMobRules.shouldTimeout(FauxMobRules.LIFETIME_TICKS + 100, false),
                "jamais de disparition observée gratuite");
        assertTrue(FauxMobRules.shouldTimeout(FauxMobRules.LIFETIME_TICKS, true));
        assertFalse(FauxMobRules.shouldTimeout(10, true), "trop jeune pour expirer");
        assertTrue(FauxMobRules.isUnseen(0.1, 5.0 * 5.0), "dos tourné = hors de vue");
        assertTrue(FauxMobRules.isUnseen(0.99, 50.0 * 50.0), "trop loin = hors de vue");
        assertFalse(FauxMobRules.isUnseen(0.99, 5.0 * 5.0));
    }

    @Test
    void tensionGainsAreBoundedAndTimeoutIsSilent() {
        for (RevealCause cause : RevealCause.values()) {
            float gain = FauxMobRules.tensionGain(cause);
            assertTrue(gain >= 0.0f && gain <= 10.0f);
        }
        assertEquals(0.0f, FauxMobRules.tensionGain(RevealCause.TIMEOUT));
        assertTrue(FauxMobRules.isSilentDissolve(RevealCause.TIMEOUT));
        assertFalse(FauxMobRules.isSilentDissolve(RevealCause.STARED));
        assertFalse(FauxMobRules.isSilentDissolve(RevealCause.STRUCK));
    }

    @Test
    void revealConstantsStayCoherent() {
        assertTrue(FauxMobRules.STARE_REVEAL_TICKS > 0);
        assertTrue(FauxMobRules.HEAD_TURN_TICKS > 0);
        assertTrue(FauxMobRules.STARE_DOT_THRESHOLD > 0.9 && FauxMobRules.STARE_DOT_THRESHOLD < 1.0);
        assertTrue(FauxMobRules.UNSEEN_DOT < FauxMobRules.STARE_DOT_THRESHOLD);
        assertTrue(FauxMobRules.TOUCH_DISTANCE < FauxMobRules.STARE_RANGE);
        assertTrue(FauxMobRules.LIFETIME_TICKS > FauxMobRules.STARE_REVEAL_TICKS);
    }
}
