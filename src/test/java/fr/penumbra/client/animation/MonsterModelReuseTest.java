package fr.penumbra.client.animation;

import fr.penumbra.client.entity.*;
import fr.penumbra.client.entity.listener.*;
import fr.penumbra.client.entity.scuttler.*;
import java.util.function.Consumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Renderers reuse their models for different entities; an attack must not leak its pose. */
class MonsterModelReuseTest {
    @Test void contrefaitResetsItsConnectedRig() {
        var root = ContrefaitModel.createBodyLayer().bakeRoot();
        check(root, new ContrefaitModel(root), new ContrefaitRenderState(), s -> {
            s.lunging = true; s.lungeProgress = .7f;
        }, s -> s.lunging = false);
    }
    @Test void originelResetsAfterBascule() {
        var root = OriginelModel.createBodyLayer().bakeRoot();
        check(root, new OriginelModel(root), new OriginelRenderState(), s -> {
            s.lunging = true; s.caveAnimation = true; s.lungeProgress = .7f;
        }, s -> { s.lunging = false; s.caveAnimation = false; });
    }
    @Test void projectionistResetsAfterDeployment() {
        var root = ProjectionistModel.createBodyLayer().bakeRoot();
        check(root, new ProjectionistModel(root), new ProjectionistRenderState(), s -> {
            s.actionStarted = true; s.actionTicks = 25;
        }, s -> { s.actionStarted = false; s.actionTicks = 0; });
    }
    @Test void listenerResetsAfterSweep() {
        var root = ListenerModel.createBodyLayer().bakeRoot();
        check(root, new ListenerModel(root), new ListenerRenderState(), s -> {
            s.behavior = 4; s.strikeTicks = 4;
        }, s -> { s.behavior = 0; s.strikeTicks = 0; });
    }
    @Test void scuttlerResetsAfterFeint() {
        var root = ScuttlerModel.createBodyLayer().bakeRoot();
        check(root, new ScuttlerModel(root), new ScuttlerRenderState(), s -> {
            s.feinting = true; s.feintTicks = 6;
        }, s -> { s.feinting = false; s.feintTicks = 0; });
    }
    @Test void absentResetsBeforeRenderingAnotherMimic() {
        var root = AbsentModel.createBodyLayer().bakeRoot();
        check(root, new AbsentModel(root), new AbsentRenderState(), s -> {
            s.absentPhase = 4; s.lunging = true; s.attackProgress = .7f;
        }, s -> { s.absentPhase = 0; s.lunging = false; s.attackProgress = 0; });
    }
    private static <S extends LivingEntityRenderState> void check(ModelPart root, EntityModel<S> model,
            S state, Consumer<S> attack, Consumer<S> reset) {
        state.ageInTicks = 60;
        model.setupAnim(state);
        var initial = root.getAllParts().stream().map(ModelPart::storePose).toList();
        attack.accept(state);
        model.setupAnim(state);
        for (var part : root.getAllParts()) {
            for (float value : new float[] {part.x, part.y, part.z, part.xRot, part.yRot, part.zRot})
                assertTrue(Float.isFinite(value));
        }
        reset.accept(state);
        model.setupAnim(state);
        assertEquals(initial, root.getAllParts().stream().map(ModelPart::storePose).toList());
    }
}
