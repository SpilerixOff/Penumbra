package fr.penumbra.photo.investigation;

import fr.penumbra.director.sequence.LinkedSceneType;
import fr.penumbra.photo.PhotoEvidence;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InvestigationRulesTest {

    @Test
    void eachClueRequiresItsExactServerEvidencePair() {
        assertTrue(InvestigationRules.unlockedClues(
                List.of(PhotoEvidence.ABSENT)).isEmpty());
        assertEquals(List.of(InvestigationClue.FALSE_GUIDE),
                InvestigationRules.unlockedClues(List.of(
                        PhotoEvidence.ABSENT, PhotoEvidence.RAMPANT)));
        assertEquals(List.of(InvestigationClue.SKITTER_OMEN),
                InvestigationRules.unlockedClues(List.of(
                        PhotoEvidence.SCUTTLER, PhotoEvidence.RAMPANT)));
        assertEquals(List.of(InvestigationClue.NEGATIVE_WITNESS),
                InvestigationRules.unlockedClues(List.of(
                        PhotoEvidence.PROJECTIONIST, PhotoEvidence.ORIGINEL)));
    }

    @Test
    void duplicateNullAndNoneEvidenceNeverInventAClue() {
        assertTrue(InvestigationRules.unlockedClues(null).isEmpty());
        assertTrue(InvestigationRules.unlockedClues(List.of(
                PhotoEvidence.NONE, PhotoEvidence.ABSENT,
                PhotoEvidence.ABSENT)).isEmpty());
    }

    @Test
    void newlyUnlockedDoesNotRepeatAnAlreadyKnownRelationship() {
        List<PhotoEvidence> before = List.of(
                PhotoEvidence.ABSENT, PhotoEvidence.RAMPANT);
        List<PhotoEvidence> after = List.of(
                PhotoEvidence.ABSENT, PhotoEvidence.RAMPANT,
                PhotoEvidence.SCUTTLER, PhotoEvidence.PROJECTIONIST,
                PhotoEvidence.ORIGINEL);

        assertEquals(List.of(InvestigationClue.SKITTER_OMEN,
                        InvestigationClue.NEGATIVE_WITNESS),
                InvestigationRules.newlyUnlocked(before, after));
    }

    @Test
    void dossierKnowledgeMapsToTheExactBehavioralCounterplay() {
        assertTrue(InvestigationRules.knowsCounterplay(
                List.of(PhotoEvidence.SCUTTLER, PhotoEvidence.RAMPANT),
                LinkedSceneType.SKITTER_OMEN));
        assertFalse(InvestigationRules.knowsCounterplay(
                List.of(PhotoEvidence.SCUTTLER, PhotoEvidence.RAMPANT),
                LinkedSceneType.FALSE_GUIDE));
        assertEquals(Counterplay.FLASH_BEFORE_FOLLOWING,
                InvestigationRules.counterplayFor(LinkedSceneType.FALSE_GUIDE));
        assertEquals(Counterplay.KEEP_CAMERA_CLOSED,
                InvestigationRules.counterplayFor(LinkedSceneType.NEGATIVE_WITNESS));
    }
}
