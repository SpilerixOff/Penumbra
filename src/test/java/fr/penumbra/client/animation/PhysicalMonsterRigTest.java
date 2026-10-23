package fr.penumbra.client.animation;

import fr.penumbra.client.entity.WatcherModel;
import fr.penumbra.client.entity.WatcherRenderState;
import fr.penumbra.client.entity.rampant.RampantModel;
import fr.penumbra.client.entity.rampant.RampantRenderState;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises baked geometry and reused model instances, not just the easing helpers. */
class PhysicalMonsterRigTest {
    @Test void ceilingReleaseAndFirstFallingFrameHaveTheSamePose() {
        var root = RampantModel.createBodyLayer().bakeRoot();
        var model = new RampantModel(root);
        var state = new RampantRenderState();
        state.ageInTicks = 100;
        state.phase = 0;
        state.phaseTicks = 100;
        state.ambushWarning = true;
        state.warningProgress = 1;
        model.setupAnim(state);
        var finalWarning = root.getAllParts().stream().map(ModelPart::storePose).toList();
        state.phase = 1;
        state.phaseTicks = 0;
        state.ambushWarning = false;
        state.dropRelease = 1;
        model.setupAnim(state);
        assertEquals(finalWarning, root.getAllParts().stream().map(ModelPart::storePose).toList());
    }
    @Test void watcherHasConnectedUpperBodyAndResetsAfterAttack() {
        var root = WatcherModel.createBodyLayer().bakeRoot();
        var torso = root.getChild("hips").getChild("torso");
        assertTrue(torso.hasChild("head") && torso.hasChild("right_arm") && torso.hasChild("left_arm"));
        var model = new WatcherModel(root);
        var state = new WatcherRenderState();
        state.distanceToCameraSq = 10000;
        model.setupAnim(state);
        var idle = root.getAllParts().stream().map(ModelPart::storePose).toList();
        state.lunging = true;
        for (int frame = 0; frame <= 120; frame++) {
            state.lungeProgress = frame / 120f;
            model.setupAnim(state);
            finite(root);
        }
        state.lunging = false;
        state.recovering = true;
        state.contacted = true;
        state.recoveryProgress = .1f;
        model.setupAnim(state);
        state.recovering = false;
        model.setupAnim(state);
        assertEquals(idle, root.getAllParts().stream().map(ModelPart::storePose).toList());
    }

    @Test void rampantEveryAttachmentPhaseUsesAConnectedBodyAndResets() {
        var root = RampantModel.createBodyLayer().bakeRoot();
        var body = root.getChild("body");
        assertTrue(body.hasChild("head") && body.hasChild("front_right_upper") && body.hasChild("spine_middle"));
        var model = new RampantModel(root);
        var state = new RampantRenderState();
        state.phase = 2;
        state.ageInTicks = 60;
        model.setupAnim(state);
        var idle = root.getAllParts().stream().map(ModelPart::storePose).toList();
        for (int phase = 0; phase <= 6; phase++) {
            state.phase = phase;
            for (int frame = 0; frame <= 80; frame++) {
                state.phaseTicks = frame / 4f;
                state.ambushWarning = true;
                state.warningProgress = frame / 80f;
                model.setupAnim(state);
                finite(root);
            }
        }
        state.phase = 2;
        state.ambushWarning = false;
        state.phaseTicks = 0;
        model.setupAnim(state);
        assertEquals(idle, root.getAllParts().stream().map(ModelPart::storePose).toList());
    }

    private static void finite(ModelPart root) {
        for (var part : root.getAllParts()) {
            for (float value : new float[] {part.x, part.y, part.z, part.xRot, part.yRot, part.zRot,
                    part.xScale, part.yScale, part.zScale}) assertTrue(Float.isFinite(value));
            assertTrue(Math.abs(part.xRot) < 4 && Math.abs(part.yRot) < 4 && Math.abs(part.zRot) < 4);
        }
    }
}
