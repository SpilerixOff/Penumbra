package fr.penumbra.client.ambient;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SceneHushTest {
    @Test void silenceExpiresAndCannotBecomeAnUnboundedMute() {
        SceneHush hush = new SceneHush();
        hush.begin(Integer.MAX_VALUE);
        for (int i = 0; i < 59; i++) { assertTrue(hush.active()); hush.tick(); }
        assertTrue(hush.active());
        hush.tick();
        assertFalse(hush.active());
        hush.begin(-10);
        assertFalse(hush.active());
    }
    @Test void smallerCueCannotShortenAStartedSilenceAndResetClearsIt() {
        SceneHush hush = new SceneHush();
        hush.begin(20);
        hush.begin(1);
        hush.tick();
        assertTrue(hush.active());
        hush.reset();
        assertFalse(hush.active());
    }
}
