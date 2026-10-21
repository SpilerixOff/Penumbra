package fr.penumbra.photo.investigation;

import fr.penumbra.director.sequence.LinkedSceneType;
import fr.penumbra.photo.PhotoEvidence;
import java.util.Set;

/** Relation deduite de deux categories serveur deja developpees. */
public enum InvestigationClue {
    FALSE_GUIDE("false_guide", LinkedSceneType.FALSE_GUIDE,
            Counterplay.FLASH_BEFORE_FOLLOWING,
            Set.of(PhotoEvidence.ABSENT, PhotoEvidence.RAMPANT)),
    SKITTER_OMEN("skitter_omen", LinkedSceneType.SKITTER_OMEN,
            Counterplay.LIGHT_THE_CEILING,
            Set.of(PhotoEvidence.SCUTTLER, PhotoEvidence.RAMPANT)),
    NEGATIVE_WITNESS("negative_witness", LinkedSceneType.NEGATIVE_WITNESS,
            Counterplay.KEEP_CAMERA_CLOSED,
            Set.of(PhotoEvidence.PROJECTIONIST, PhotoEvidence.ORIGINEL));

    private final String id;
    private final LinkedSceneType sceneType;
    private final Counterplay counterplay;
    private final Set<PhotoEvidence> requiredEvidence;

    InvestigationClue(String id, LinkedSceneType sceneType, Counterplay counterplay,
                      Set<PhotoEvidence> requiredEvidence) {
        this.id = id;
        this.sceneType = sceneType;
        this.counterplay = counterplay;
        this.requiredEvidence = Set.copyOf(requiredEvidence);
    }

    public String id() {
        return id;
    }

    public LinkedSceneType sceneType() {
        return sceneType;
    }

    public Counterplay counterplay() {
        return counterplay;
    }

    public Set<PhotoEvidence> requiredEvidence() {
        return requiredEvidence;
    }

    public String translationKey() {
        return "dossier.penumbra.clue." + id;
    }
}
