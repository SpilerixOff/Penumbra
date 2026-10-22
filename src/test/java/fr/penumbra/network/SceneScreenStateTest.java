package fr.penumbra.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneScreenStateTest {
    @Test void occupiedScreenRevealsOnlyAfterClosingAndOnlyOnce() {
        SceneScreenState state = new SceneScreenState();
        state.accept(1, 10, "overworld");
        assertTrue(state.blocks(25, "overworld"));
        assertFalse(state.consumeClose(25, "overworld"));
        state.accept(1, 40, "overworld");
        state.accept(0, 51, "overworld");
        assertFalse(state.blocks(51, "overworld"));
        assertTrue(state.consumeClose(55, "overworld"));
        assertFalse(state.consumeClose(56, "overworld"));
    }
    @Test void pauseOrSettingsCancelTheReadingSequence() {
        SceneScreenState state = new SceneScreenState();
        state.accept(1, 10, "overworld");
        state.accept(2, 60, "overworld");
        state.accept(0, 70, "overworld");
        assertFalse(state.consumeClose(70, "overworld"));
    }
    @Test void expiredHeartbeatOrDimensionChangeCannotReleaseAnOldTrap() {
        SceneScreenState state = new SceneScreenState();
        state.accept(1, 10, "overworld");
        state.accept(0, 150, "overworld");
        assertFalse(state.consumeClose(150, "overworld"));
        state.accept(1, 160, "overworld");
        state.accept(0, 210, "nether");
        assertFalse(state.consumeClose(210, "nether"));
    }
    @Test void prolongedReadingAndBriefInventoryTapsAreNotScares() {
        SceneScreenState state = new SceneScreenState();
        state.accept(1, 10, "world");
        state.accept(0, 20, "world");
        assertFalse(state.consumeClose(20, "world"));
        state.accept(1, 30, "world");
        for (int time = 70; time <= 670; time += 40) state.accept(1, time, "world");
        state.accept(0, 680, "world");
        assertFalse(state.consumeClose(680, "world"));
    }
    @Test void closeExpiresAndClockRollbackDiscardsState() {
        SceneScreenState state = new SceneScreenState();
        state.accept(1, 10, "world");
        state.accept(0, 50, "world");
        assertFalse(state.consumeClose(71, "world"));
        state.accept(1, 90, "world");
        state.accept(0, 40, "world");
        assertFalse(state.consumeClose(40, "world"));
    }
    @Test void unknownScreenKindsBlockWithoutArmingAReadingTrap() {
        SceneScreenState state = new SceneScreenState();
        state.accept(-1, 0, "world");
        assertTrue(state.blocks(20, "world"));
        state.accept(0, 50, "world");
        assertFalse(state.consumeClose(50, "world"));
    }
}
