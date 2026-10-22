package fr.penumbra.photo.investigation;

import fr.penumbra.director.sequence.LinkedSceneType;
import fr.penumbra.photo.PhotoEvidence;
import java.util.List;
import java.util.Arrays;
import java.util.EnumSet;
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

    @Test
    void emptyOrUnrelatedEvidenceDoesNotRevealUnseenRelationships() {
        assertTrue(InvestigationRules.partialLeads(null).isEmpty());
        assertTrue(InvestigationRules.partialLeads(List.of(
                PhotoEvidence.NONE, PhotoEvidence.HOST, PhotoEvidence.LISTENER)).isEmpty());
    }

    @Test
    void partialLeadNamesOnlyObservedEvidenceAndDoesNotGrantCounterplay() {
        List<InvestigationLead> leads = InvestigationRules.partialLeads(Arrays.asList(
                PhotoEvidence.ABSENT, null, PhotoEvidence.NONE, PhotoEvidence.ABSENT));
        assertEquals(List.of(new InvestigationLead(InvestigationClue.FALSE_GUIDE,
                PhotoEvidence.ABSENT)), leads);
        assertFalse(InvestigationRules.knowsCounterplay(
                List.of(PhotoEvidence.ABSENT), LinkedSceneType.FALSE_GUIDE));
        assertEquals("dossier.penumbra.lead.false_guide.absent", leads.getFirst().translationKey());
    }

    @Test
    void completingOnePairReplacesOnlyItsOwnLead() {
        assertEquals(2, InvestigationRules.partialLeads(List.of(PhotoEvidence.RAMPANT)).size());
        List<PhotoEvidence> withAbsent = List.of(PhotoEvidence.RAMPANT, PhotoEvidence.ABSENT);
        assertEquals(List.of(new InvestigationLead(InvestigationClue.SKITTER_OMEN,
                        PhotoEvidence.RAMPANT)), InvestigationRules.partialLeads(withAbsent));
        assertEquals(List.of(InvestigationClue.FALSE_GUIDE),
                InvestigationRules.unlockedClues(withAbsent));
        assertTrue(InvestigationRules.partialLeads(EnumSet.allOf(PhotoEvidence.class)).isEmpty());
    }
}
