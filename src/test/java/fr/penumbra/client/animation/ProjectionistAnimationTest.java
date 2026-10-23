package fr.penumbra.client.animation;

import fr.penumbra.entity.projectionist.ProjectionistTiming;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectionistAnimationTest {
    @Test
    void cameraArmsBeforeMovementAndHasABoundedRetreatPhase() {
        for (int ticks = 0; ticks <= ProjectionistTiming.ARM_TICKS; ticks++) {
            assertEquals(0.0, ProjectionistTiming.advanceStep(ticks));
        }
        double totalAdvance = 0;
        for (int ticks = 0; ticks <= ProjectionistTiming.TOTAL_TICKS; ticks++) {
            double step = ProjectionistTiming.advanceStep(ticks);
            assertTrue(step >= 0 && step <= 0.22);
            totalAdvance += step;
            if (ticks > ProjectionistTiming.RECOVERY_START) assertEquals(0.0, step);
        }
        assertTrue(totalAdvance > 2.0 && totalAdvance < 3.5);
    }

    @Test
    void armDeployAndRecoilRemainContinuous() {
        for (float boundary : new float[]{12.0f, 30.0f, 46.0f}) {
            var before = ProjectionistAnimation.pose(boundary - 0.0001f);
            var after = ProjectionistAnimation.pose(boundary + 0.0001f);
            assertEquals(before.torsoPitch(), after.torsoPitch(), 0.001f);
            assertEquals(before.lensExtension(), after.lensExtension(), 0.001f);
            assertEquals(before.rightArmPitch(), after.rightArmPitch(), 0.001f);
            assertEquals(before.coatOpen(), after.coatOpen(), 0.001f);
        }
    }

    @Test
    void deploymentIsAsymmetricAndDoesNotLoopAfterItsEnd() {
        var deployed = ProjectionistAnimation.pose(30.0f);
        assertTrue(Math.abs(deployed.rightArmPitch() - deployed.leftArmPitch()) > 0.7f);
        assertTrue(Math.abs(deployed.rightArmRoll()) > Math.abs(deployed.leftArmRoll()));
        assertTrue(deployed.coatOpen() > 0.9f);
        assertEquals(ProjectionistAnimation.pose(46), ProjectionistAnimation.pose(2000));
        assertEquals(ProjectionistAnimation.pose(0), ProjectionistAnimation.pose(Float.NaN));
    }

    @Test
    void reelsWindContinuouslyAndStopWithoutReversing() {
        float last = 0;
        for (int i = 0; i <= 460; i++) {
            float current = ProjectionistAnimation.reelRotation(i / 10.0f);
            assertTrue(current >= last - 0.0001f);
            assertTrue(current - last < 0.04f);
            last = current;
        }
        assertEquals(last, ProjectionistAnimation.reelRotation(100));
    }
}
