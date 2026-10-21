package fr.penumbra.network;

import org.junit.jupiter.api.Test;

import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PenumbraNetworkingTest {

    @Test
    void positionalSoundRangeMatchesVanillaStyleAttenuationAndStaysBounded() {
        assertEquals(16.0, PenumbraNetworking.audibleRadius(0.2f));
        assertEquals(16.0, PenumbraNetworking.audibleRadius(1.0f));
        assertEquals(48.0, PenumbraNetworking.audibleRadius(3.0f));
        assertEquals(64.0, PenumbraNetworking.audibleRadius(20.0f));
    }

    @Test
    void distantSoundKeepsDirectionButIsMovedInsideClientAttenuationRange() {
        Vec3 listener = new Vec3(0.0, 64.0, 0.0);
        Vec3 distant = new Vec3(0.0, 64.0, 100.0);

        Vec3 adjusted = PenumbraNetworking.spatializeForListener(listener, distant, 0.5f);
        assertEquals(0.0, adjusted.x, 1.0e-9);
        assertEquals(64.0, adjusted.y, 1.0e-9);
        assertEquals(12.0, adjusted.z, 1.0e-9); // 75 % de la portée vanilla de 16 blocs

        Vec3 nearby = new Vec3(3.0, 64.0, 4.0);
        assertEquals(nearby, PenumbraNetworking.spatializeForListener(listener, nearby, 0.5f));
    }

    @Test
    void companionSharingStopsAtItsOwnBoundaryWithoutBroadcastingToTheLevel() {
        Vec3 source = new Vec3(0.0, 64.0, 0.0);
        Vec3 distantSound = new Vec3(100.0, 64.0, 0.0);

        assertTrue(PenumbraNetworking.shouldReceiveSound(
                new Vec3(24.0, 64.0, 0.0), source, distantSound, 0.5f));
        assertFalse(PenumbraNetworking.shouldReceiveSound(
                new Vec3(24.01, 64.0, 0.0), source, distantSound, 0.5f));
        assertTrue(PenumbraNetworking.shouldReceiveSound(
                new Vec3(88.0, 64.0, 0.0), source, distantSound, 0.5f));
    }

    @Test
    void physicalSoundDependsOnlyOnItsRealOrigin() {
        Vec3 origin = new Vec3(100.0, 64.0, 0.0);
        assertTrue(PenumbraNetworking.shouldReceiveSpatialSound(
                new Vec3(84.0, 64.0, 0.0), origin, 1.0f));
        assertFalse(PenumbraNetworking.shouldReceiveSpatialSound(
                new Vec3(24.0, 64.0, 0.0), origin, 1.0f));
    }

    @Test
    void malformedJumpscareIntensityIsNeutralizedBeforeNetworking() {
        assertEquals(0.0f, PenumbraNetworking.sanitizeJumpscareIntensity(Float.NaN));
        assertEquals(0.0f, PenumbraNetworking.sanitizeJumpscareIntensity(Float.POSITIVE_INFINITY));
        assertEquals(0.0f, PenumbraNetworking.sanitizeJumpscareIntensity(-2.0f));
        assertEquals(1.0f, PenumbraNetworking.sanitizeJumpscareIntensity(2.0f));
        assertTrue(PenumbraNetworking.isKnownJumpscare("lunge_screamer"));
        assertTrue(PenumbraNetworking.isKnownJumpscare("rampant_screamer"));
        assertTrue(PenumbraNetworking.isKnownJumpscare("absent_screamer"));
        assertTrue(PenumbraNetworking.isKnownJumpscare("chorus_screamer"));
        assertFalse(PenumbraNetworking.isKnownJumpscare("forged_packet"));
    }
}
