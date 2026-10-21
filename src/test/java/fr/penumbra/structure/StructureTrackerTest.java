package fr.penumbra.structure;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructureTrackerTest {
    @org.junit.jupiter.api.BeforeAll
    static void bootstrapRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void finalRetryCanUseCompletedAltarWithoutResettingOtherPlayers() {
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        var altar = new StructureTracker.Entry(StructureType.ASH_SANCTUM.id(),
                new BlockPos(0, 64, 0), Level.OVERWORLD, 0, false);
        var entries = new com.google.gson.JsonArray();
        entries.add(StructureTracker.Entry.CODEC.encodeStart(
                com.mojang.serialization.JsonOps.INSTANCE, altar).getOrThrow());
        var saved = new com.google.gson.JsonObject();
        saved.add("entries", entries);
        var tracker = StructureTracker.CODEC.parse(
                com.mojang.serialization.JsonOps.INSTANCE, saved).getOrThrow();
        tracker.markSanctumActivated(owner, altar);
        assertTrue(tracker.findSanctumAt(owner, Level.OVERWORLD, altar.pos()).isEmpty());
        assertTrue(tracker.findSanctumAt(owner, Level.OVERWORLD, altar.pos(), true).isPresent());
        assertTrue(tracker.findSanctumAt(other, Level.OVERWORLD, altar.pos()).isPresent());
        assertTrue(tracker.findSanctumAt(owner, Level.NETHER, altar.pos(), true).isEmpty());
        assertTrue(tracker.findSanctumAt(owner, Level.OVERWORLD, altar.pos().offset(10, 0, 0), true).isEmpty());
    }
}
