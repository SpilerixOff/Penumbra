package fr.penumbra.structure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PenumbraStructuresTest {

    @Test
    void rarityIsAnAreaRatherThanASideLength() {
        assertEquals(30, PenumbraStructures.cellSideForRarity(900));
        assertEquals(14, PenumbraStructures.cellSideForRarity(200));
        assertEquals(71, PenumbraStructures.cellSideForRarity(5000));
    }

    @Test
    void rarityKeepsASafeMinimumCellSize() {
        assertEquals(8, PenumbraStructures.cellSideForRarity(0));
        assertEquals(8, PenumbraStructures.cellSideForRarity(64));
    }
}
