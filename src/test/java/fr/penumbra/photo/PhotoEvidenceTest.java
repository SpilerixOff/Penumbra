package fr.penumbra.photo;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoEvidenceTest {

    @Test
    void idsAreStableCaseInsensitiveAndUnknownSafe() {
        assertEquals(PhotoEvidence.ORIGINEL, PhotoEvidence.byId("ORIGINEL"));
        assertEquals(PhotoEvidence.NONE, PhotoEvidence.byId("future_monster"));
        assertEquals(PhotoEvidence.NONE, PhotoEvidence.byId(null));
        assertFalse(PhotoEvidence.NONE.discoverable());
        assertTrue(PhotoEvidence.ABSENT.discoverable());
    }

    @Test
    void dossierStagesUseExactThresholds() {
        assertEquals(0, PhotoEvidence.dossierStage(0));
        assertEquals(1, PhotoEvidence.dossierStage(1));
        assertEquals(1, PhotoEvidence.dossierStage(2));
        assertEquals(2, PhotoEvidence.dossierStage(3));
        assertEquals(3, PhotoEvidence.dossierStage(5));
        assertEquals(4, PhotoEvidence.dossierStage(7));
        assertEquals(4, PhotoEvidence.dossierStage(99));
    }

    @Test
    void listenerHasAStableIdAndDossierNormalizationRetainsAllCategories() {
        List<PhotoEvidence> all = Arrays.stream(PhotoEvidence.values())
                .filter(PhotoEvidence::discoverable).toList();
        assertEquals(PhotoEvidence.LISTENER, PhotoEvidence.byId("listener"));
        assertEquals(10, PhotoEvidence.discoverableCount());
        assertEquals(all, PhotoEvidence.dossierFromIds(all.stream().map(PhotoEvidence::id).toList()));
    }

    @Test
    void dossierIdsIgnoreNullUnknownAndDuplicateValues() {
        assertEquals(List.of(PhotoEvidence.LISTENER, PhotoEvidence.HOST),
                PhotoEvidence.dossierFromIds(Arrays.asList("listener", null, "none", "HOST",
                        "future_creature", "host", "listener")));
        assertTrue(PhotoEvidence.dossierFromIds(null).isEmpty());
    }
}
