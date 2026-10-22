package fr.penumbra.client.light;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LightSectionUpdatesTest {
    @Test
    void reorderAndDuplicateSourcesDoNotRebuildTerrain() {
        var a = new LightSectionUpdates.Cell(1, 64, 1, 14);
        var b = new LightSectionUpdates.Cell(50, 64, 1, 14);
        assertTrue(LightSectionUpdates.changed(List.of(a, b), List.of(b, a, a)).isEmpty());
    }

    @Test
    void manyLightsInOneSectionRebuildEachSectionOnce() {
        var sources = java.util.stream.IntStream.range(0, 16)
                .mapToObj(x -> new LightSectionUpdates.Cell(x, 64, 1, 14)).toList();
        assertEquals(27, LightSectionUpdates.changed(List.of(), sources).size());
    }

    @Test
    void movingAcrossBoundaryInvalidatesOldAndNewReach() {
        var before = new LightSectionUpdates.Cell(15, 64, 0, 14);
        var after = new LightSectionUpdates.Cell(16, 64, 0, 14);
        var dirty = LightSectionUpdates.changed(List.of(before), List.of(after));
        assertEquals(36, dirty.size());
        assertTrue(dirty.contains(new LightSectionUpdates.Section(-1, 4, 0)));
        assertTrue(dirty.contains(new LightSectionUpdates.Section(2, 4, 0)));
    }

    @Test
    void removedOrDimmedLightStillInvalidatesItsTerrain() {
        var before = new LightSectionUpdates.Cell(0, 64, 0, 14);
        var after = new LightSectionUpdates.Cell(0, 64, 0, 9);
        assertEquals(27, LightSectionUpdates.changed(List.of(before), List.of(after)).size());
        assertEquals(27, LightSectionUpdates.changed(List.of(before), List.of()).size());
    }

    @Test
    void negativeCoordinatesUseFloorAndAreSymmetric() {
        var negative = new LightSectionUpdates.Cell(-1, -1, -17, 14);
        Set<LightSectionUpdates.Section> dirty = LightSectionUpdates.changed(List.of(), List.of(negative));
        assertTrue(dirty.contains(new LightSectionUpdates.Section(-2, -2, -3)));
        assertFalse(dirty.contains(new LightSectionUpdates.Section(1, 0, 0)));
        assertEquals(dirty, LightSectionUpdates.changed(List.of(negative), List.of()));
    }
}
