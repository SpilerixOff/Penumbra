package fr.penumbra.event.personal;

import fr.penumbra.photo.FauxPhotoPainter;
import fr.penumbra.photo.PhotoAlbumState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests des Échos (0.16.0) : photos peintes, veille nocturne, écho d'actions. */
class EchoesTest {

    private static final UUID PLAYER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    @AfterEach
    void cleanup() {
        NightWatchLog.clearAll();
        ActionEchoLog.clearAll();
    }

    // ================= FauxPhotoPainter =================

    @Test
    void paintedPhotosMatchAlbumFormatAndAreDeterministic() {
        byte[] bedroom = FauxPhotoPainter.bedroomAtNight(42L);
        byte[] outside = FauxPhotoPainter.baseFromOutside(42L);
        assertEquals(PhotoAlbumState.RGB_BYTES, bedroom.length);
        assertEquals(PhotoAlbumState.RGB_BYTES, outside.length);
        assertArrayEquals(bedroom, FauxPhotoPainter.bedroomAtNight(42L),
                "même graine, même cliché");
        assertFalse(java.util.Arrays.equals(bedroom, FauxPhotoPainter.bedroomAtNight(43L)),
                "graines différentes, clichés différents");
    }

    @Test
    void paintedPhotosStayDarkWithAFewPaleAndWarmPixels() {
        byte[] bedroom = FauxPhotoPainter.bedroomAtNight(7L);
        int bright = 0;
        long sum = 0;
        for (int i = 0; i < bedroom.length; i += 3) {
            int r = bedroom[i] & 0xFF;
            sum += r;
            if (r > 80) {
                bright++;
            }
        }
        double meanRed = sum / (double) (bedroom.length / 3);
        assertTrue(meanRed < 40.0, "le cliché doit rester très sombre");
        assertTrue(bright > 0, "la silhouette pâle doit exister");
        assertTrue(bright < 900, "la silhouette doit rester presque rien");

        byte[] outside = FauxPhotoPainter.baseFromOutside(7L);
        boolean warmWindow = false;
        for (int i = 0; i < outside.length; i += 3) {
            int r = outside[i] & 0xFF;
            int b = outside[i + 2] & 0xFF;
            if (r > 130 && r > b + 60) {
                warmWindow = true;
                break;
            }
        }
        assertTrue(warmWindow, "une fenêtre chaude doit être allumée");
    }

    // ================= NightWatchLog =================

    @Test
    void nightWatchCountsApproachesByEdgeAndKeepsClosest() {
        long day = 0;
        NightWatchLog.sample(PLAYER, false, Double.NaN);
        assertNull(NightWatchLog.summaryFor(PLAYER));
        // La nuit tombe : deux approches distinctes, au plus près 6 blocs.
        NightWatchLog.sample(PLAYER, true, Double.NaN);
        NightWatchLog.sample(PLAYER, true, 20.0 * 20.0);
        NightWatchLog.sample(PLAYER, true, 6.0 * 6.0);
        NightWatchLog.sample(PLAYER, true, Double.NaN);
        NightWatchLog.sample(PLAYER, true, 15.0 * 15.0);
        // L'aube : le bilan se fige.
        NightWatchLog.sample(PLAYER, false, Double.NaN);
        NightWatchLog.NightSummary summary = NightWatchLog.summaryFor(PLAYER);
        assertNotNull(summary);
        assertEquals(2, summary.approaches());
        assertEquals(6, summary.closestBlocks());
    }

    @Test
    void quietNightReportsZeroApproaches() {
        NightWatchLog.sample(PLAYER, true, Double.NaN);
        NightWatchLog.sample(PLAYER, true, 60.0 * 60.0); // au-delà du rayon : rien
        NightWatchLog.sample(PLAYER, false, Double.NaN);
        NightWatchLog.NightSummary summary = NightWatchLog.summaryFor(PLAYER);
        assertNotNull(summary);
        assertEquals(0, summary.approaches());
        assertFalse(NightWatchLog.isNear(Double.NaN));
        assertFalse(NightWatchLog.isNear(60.0 * 60.0));
        assertTrue(NightWatchLog.isNear(10.0 * 10.0));
    }

    // ================= ActionEchoLog =================

    @Test
    void echoLogKeepsWindowAndCap() {
        for (int i = 0; i < 12; i++) {
            ActionEchoLog.note(PLAYER, ActionEchoLog.Action.DOOR, 1000L + i * 100L);
        }
        List<ActionEchoLog.Note> recent = ActionEchoLog.recentFor(PLAYER, 2200L);
        assertEquals(ActionEchoLog.MAX_NOTES, recent.size(), "mémoire bornée");
        // Bien après la fenêtre : plus rien.
        assertTrue(ActionEchoLog.recentFor(PLAYER,
                2200L + ActionEchoLog.WINDOW_TICKS + 1000L).isEmpty());
    }

    @Test
    void replayOffsetsPreserveOrderAndRespectBounds() {
        ActionEchoLog.note(PLAYER, ActionEchoLog.Action.DOOR, 0L);
        ActionEchoLog.note(PLAYER, ActionEchoLog.Action.CHEST, 6000L);
        ActionEchoLog.note(PLAYER, ActionEchoLog.Action.TRAPDOOR, 11000L);
        List<ActionEchoLog.Note> notes = ActionEchoLog.recentFor(PLAYER, 11000L);
        long[] offsets = ActionEchoLog.replayOffsets(notes);
        assertEquals(3, offsets.length);
        assertEquals(0L, offsets[0]);
        for (int i = 1; i < offsets.length; i++) {
            assertTrue(offsets[i] - offsets[i - 1] >= ActionEchoLog.MIN_REPLAY_GAP_TICKS,
                    "écart minimal respecté");
        }
        assertTrue(offsets[offsets.length - 1]
                        <= ActionEchoLog.MAX_REPLAY_SPAN_TICKS + ActionEchoLog.MIN_REPLAY_GAP_TICKS * 3,
                "séquence compressée dans la fenêtre de rejeu");
        assertEquals(0, ActionEchoLog.replayOffsets(List.of()).length);
    }

    @Test
    void consumePreventsImmediateSecondEcho() {
        ActionEchoLog.note(PLAYER, ActionEchoLog.Action.DOOR, 100L);
        ActionEchoLog.consume(PLAYER);
        assertTrue(ActionEchoLog.recentFor(PLAYER, 100L).isEmpty());
    }
}
