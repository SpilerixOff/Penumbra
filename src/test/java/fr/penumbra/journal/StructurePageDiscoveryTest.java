package fr.penumbra.journal;

import com.mojang.serialization.JsonOps;
import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StructurePageDiscoveryTest {
    private static final BlockPos ANCHOR = new BlockPos(100, 64, -200);

    @BeforeAll
    static void bootstrapRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void everyPostAndHouseVariantUsesItsActualChestAnchor() {
        for (StructureType type : List.of(StructureType.OBSERVATION_POST, StructureType.SHIFTED_HOUSE)) {
            for (int variant = 0; variant < type.variantCount(); variant++) {
                var structure = entry(type, variant);
                assertEquals(JournalEntry.pageFor(type), StructurePageDiscovery.pageAt(
                        List.of(structure), Level.OVERWORLD, ANCHOR));
            }
        }
    }

    @Test
    void sanctumRequiresTheChestBesideItsPillarForBothVariants() {
        for (int variant = 0; variant < 2; variant++) {
            var entries = List.of(entry(StructureType.ASH_SANCTUM, variant));
            assertEquals(JournalEntry.METHOD_STILLNESS,
                    StructurePageDiscovery.pageAt(entries, Level.OVERWORLD, ANCHOR.offset(2, 0, 0)).orElseThrow());
            assertTrue(StructurePageDiscovery.pageAt(entries, Level.OVERWORLD, ANCHOR).isEmpty());
        }
    }

    @Test
    void wellRequiresReachingTheGalleryAtItsRealDepthNotTheSurface() {
        var shallow = List.of(entry(StructureType.PENUMBRA_WELL, 0));
        var deep = List.of(entry(StructureType.PENUMBRA_WELL, 1));
        assertEquals(JournalEntry.LAST_PAGE, StructurePageDiscovery.pageAt(
                shallow, Level.OVERWORLD, ANCHOR.offset(0, -20, -3)).orElseThrow());
        assertEquals(JournalEntry.LAST_PAGE, StructurePageDiscovery.pageAt(
                deep, Level.OVERWORLD, ANCHOR.offset(3, -30, 0)).orElseThrow());
        assertTrue(StructurePageDiscovery.pageAt(deep, Level.OVERWORLD, ANCHOR).isEmpty());
        assertTrue(StructurePageDiscovery.pageAt(deep, Level.OVERWORLD, ANCHOR.offset(0, -20, -3)).isEmpty());
    }

    @Test
    void unrelatedChestOtherDimensionAndUnknownStructuresRevealNothing() {
        var entries = List.of(entry(StructureType.OBSERVATION_POST, 0));
        assertTrue(StructurePageDiscovery.pageAt(entries, Level.OVERWORLD, ANCHOR.above()).isEmpty());
        assertTrue(StructurePageDiscovery.pageAt(entries, Level.NETHER, ANCHOR).isEmpty());
        assertTrue(StructurePageDiscovery.pageAt(List.of(entry(StructureType.SIGNAL_PYRE, 0)),
                Level.OVERWORLD, ANCHOR).isEmpty());
        assertTrue(StructurePageDiscovery.pageAt(List.of(entry(StructureType.PENUMBRA_WELL, 99)),
                Level.OVERWORLD, ANCHOR).isEmpty());
        assertTrue(StructurePageDiscovery.pageAt(List.of(new StructureTracker.Entry(
                "unknown", ANCHOR, Level.OVERWORLD, 0, false)), Level.OVERWORLD, ANCHOR).isEmpty());
    }

    @Test
    void twoInvestigatorsRecoverIndependentlyAtTheSameLootedSiteAfterReload() {
        UUID first = new UUID(0L, 1L);
        UUID lateJoiner = new UUID(0L, 2L);
        var structures = List.of(entry(StructureType.SHIFTED_HOUSE, 1));
        var knowledge = new JournalKnowledge();
        assertTrue(StructurePageDiscovery.recoverPage(knowledge, first, structures,
                Level.OVERWORLD, ANCHOR).isPresent());
        assertFalse(knowledge.hasPage(lateJoiner, JournalEntry.METHOD_REFUGE));
        assertTrue(StructurePageDiscovery.recoverPage(knowledge, first, structures,
                Level.OVERWORLD, ANCHOR).isEmpty(), "No repeated page notification or reward");

        var encoded = JournalKnowledge.CODEC.encodeStart(JsonOps.INSTANCE, knowledge).getOrThrow();
        knowledge = JournalKnowledge.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        // Recovery has no shared-loot argument and never consumes the site's evidence.
        assertTrue(StructurePageDiscovery.recoverPage(knowledge, lateJoiner, structures,
                Level.OVERWORLD, ANCHOR).isPresent());
        assertEquals(1, knowledge.progress(first));
        assertEquals(1, knowledge.progress(lateJoiner));
        assertFalse(knowledge.hasPage(lateJoiner, JournalEntry.METHOD_CAMERA));
        assertTrue(StructurePageDiscovery.recoverPage(knowledge, lateJoiner, structures,
                Level.OVERWORLD, ANCHOR.above()).isEmpty());
    }

    private static StructureTracker.Entry entry(StructureType type, int variant) {
        return new StructureTracker.Entry(type.id(), ANCHOR, Level.OVERWORLD, variant, false);
    }
}
