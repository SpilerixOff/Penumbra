package fr.penumbra.client.animation;

import fr.penumbra.entity.scuttler.ScuttlerFeint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScuttlerAnimationTest {
    @Test
    void eightLegsAdvanceInWavesInsteadOfTwoRigidGroups() {
        for (int leg = 0; leg < 3; leg++) {
            assertEquals(1.28f, ScuttlerAnimation.legPhase(0, leg + 1, true)
                    - ScuttlerAnimation.legPhase(0, leg, true), 0.0001f);
            assertEquals((float) Math.PI, ScuttlerAnimation.legPhase(0, leg, false)
                    - ScuttlerAnimation.legPhase(0, leg, true), 0.0001f);
        }
        assertEquals(ScuttlerAnimation.legPhase(0, 0, true),
                ScuttlerAnimation.legPhase(Float.NaN, 0, true));
    }

    @Test
    void oneRiseAndSlapFinishesBeforeRetreat() {
        assertEquals(0.0f, ScuttlerAnimation.feint(ScuttlerFeint.BRAKE_TICKS).rise());
        assertEquals(1.0f, ScuttlerAnimation.feint(ScuttlerFeint.RAISE_END_TICK).rise());
        assertEquals(0.0f, ScuttlerAnimation.feint(ScuttlerFeint.RECOIL_START_TICK).rise());
        assertEquals(1.0f, ScuttlerAnimation.feint(ScuttlerFeint.RECOIL_START_TICK).slap());
        assertEquals(new ScuttlerAnimation.Feint(0, 0, 0, 1),
                ScuttlerAnimation.feint(ScuttlerFeint.TOTAL_TICKS));
    }

    @Test
    void noPoseSnapsAtBrakeRiseSlapOrDeparture() {
        for (float tick : new float[] {4, 9, 12, 20}) {
            var before = ScuttlerAnimation.feint(tick - 0.0001f);
            var after = ScuttlerAnimation.feint(tick + 0.0001f);
            assertEquals(before.rise(), after.rise(), 0.0001f);
            assertEquals(before.crouch(), after.crouch(), 0.0001f);
            assertEquals(before.slap(), after.slap(), 0.0001f);
        }
        assertEquals(ScuttlerAnimation.feint(0), ScuttlerAnimation.feint(Float.NaN));
        assertEquals(ScuttlerAnimation.feint(20), ScuttlerAnimation.feint(100));
    }
}
