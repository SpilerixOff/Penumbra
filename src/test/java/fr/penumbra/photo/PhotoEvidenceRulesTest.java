package fr.penumbra.photo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhotoEvidenceRulesTest {

    @Test
    void acceptsVisibleCenteredSubjectInsideRange() {
        assertTrue(PhotoEvidenceRules.isFramed(24.0, 1.0, true));
        assertTrue(PhotoEvidenceRules.isFramed(
                PhotoEvidenceRules.MAX_DISTANCE, PhotoEvidenceRules.MIN_ALIGNMENT, true));
    }

    @Test
    void rejectsBehindOutsideInvisibleAndInvalidSubjects() {
        assertFalse(PhotoEvidenceRules.isFramed(10.0, -1.0, true));
        assertFalse(PhotoEvidenceRules.isFramed(65.0, 1.0, true));
        assertFalse(PhotoEvidenceRules.isFramed(10.0, 1.0, false));
        assertFalse(PhotoEvidenceRules.isFramed(Double.NaN, 1.0, true));
        assertFalse(PhotoEvidenceRules.isFramed(10.0, Double.POSITIVE_INFINITY, true));
    }

    @Test
    void zoomNarrowsTheEvidenceConeToTheCapturedImage() {
        double alignmentAtTwentyDegrees = Math.cos(Math.toRadians(20.0));
        assertTrue(PhotoEvidenceRules.isFramed(20.0, alignmentAtTwentyDegrees, true, 1.0f));
        assertFalse(PhotoEvidenceRules.isFramed(20.0, alignmentAtTwentyDegrees, true, 4.0f));
        assertTrue(PhotoEvidenceRules.minimumAlignment(4.0f)
                > PhotoEvidenceRules.minimumAlignment(1.0f));
    }

    @Test
    void higherPriorityWinsBeforeCenteringAndDistance() {
        double originelAtEdge = PhotoEvidenceRules.score(
                PhotoEvidence.ORIGINEL, 60.0, PhotoEvidenceRules.MIN_ALIGNMENT);
        double hostCentered = PhotoEvidenceRules.score(PhotoEvidence.HOST, 2.0, 1.0);
        assertTrue(originelAtEdge > hostCentered);
    }

    @Test
    void developmentGateHandlesBoundaryAndClockRollback() {
        assertFalse(PhotoEvidenceRules.isDeveloped(1_199L, 1_000L, 200));
        assertTrue(PhotoEvidenceRules.isDeveloped(1_200L, 1_000L, 200));
        assertFalse(PhotoEvidenceRules.isDeveloped(999L, 1_000L, 200));
    }
}
