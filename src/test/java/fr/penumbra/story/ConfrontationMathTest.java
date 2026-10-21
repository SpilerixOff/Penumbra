package fr.penumbra.story;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Géométrie pure de la Confrontation : le reflet autour du centre du rituel. */
class ConfrontationMathTest {

    private static final Vec3 CENTER = new Vec3(100.0, 64.0, 100.0);

    @Test
    void mirrorIsPointSymmetryAroundCenter() {
        Vec3 player = new Vec3(110.0, 64.0, 92.0);
        Vec3 mirrored = ConfrontationManager.mirrorPosition(CENTER, player, 5.0);
        assertEquals(90.0, mirrored.x, 1.0E-6);
        assertEquals(108.0, mirrored.z, 1.0E-6);
        assertEquals(CENTER.y, mirrored.y, 1.0E-6);
    }

    @Test
    void mirrorNeverLetsTheCopyReachThePlayer() {
        // Joueur pile au centre : le reflet nu serait au même point.
        Vec3 atCenter = new Vec3(CENTER.x, 64.0, CENTER.z);
        Vec3 mirrored = ConfrontationManager.mirrorPosition(CENTER, atCenter, 5.0);
        double dx = mirrored.x - atCenter.x;
        double dz = mirrored.z - atCenter.z;
        assertTrue(Math.sqrt(dx * dx + dz * dz) >= 5.0,
                "l'écartement minimal doit être imposé");
        // Joueur à 1 bloc du centre : croisement — même garantie.
        Vec3 close = new Vec3(CENTER.x + 1.0, 64.0, CENTER.z);
        Vec3 mirroredClose = ConfrontationManager.mirrorPosition(CENTER, close, 5.0);
        double dx2 = mirroredClose.x - close.x;
        double dz2 = mirroredClose.z - close.z;
        assertTrue(Math.sqrt(dx2 * dx2 + dz2 * dz2) >= 5.0);
    }

    @Test
    void mirrorDistanceEqualsPlayerDistanceWhenFar() {
        Vec3 player = new Vec3(112.0, 64.0, 100.0);   // à 12 blocs du centre
        Vec3 mirrored = ConfrontationManager.mirrorPosition(CENTER, player, 5.0);
        double copyDist = Math.abs(mirrored.x - CENTER.x);
        assertEquals(12.0, copyDist, 1.0E-6,
                "elle se tient exactement aussi loin que vous");
    }

    @Test
    void yawFacesTheTarget() {
        // Cible plein sud (+z) : yaw Minecraft attendu ≈ 0.
        float south = ConfrontationManager.yawTowards(
                new Vec3(0, 0, 0), new Vec3(0, 0, 10));
        assertEquals(0.0f, normalize(south), 0.01f);
        // Cible plein ouest (-x) : yaw attendu ≈ 90.
        float west = ConfrontationManager.yawTowards(
                new Vec3(0, 0, 0), new Vec3(-10, 0, 0));
        assertEquals(90.0f, normalize(west), 0.01f);
    }

    private static float normalize(float yaw) {
        float value = yaw % 360.0f;
        if (value < -180.0f) {
            value += 360.0f;
        }
        if (value > 180.0f) {
            value -= 360.0f;
        }
        return value;
    }
}
