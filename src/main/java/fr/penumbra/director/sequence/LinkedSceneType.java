package fr.penumbra.director.sequence;

import java.util.Arrays;

/** Les trois chaines narratives bornees de la 0.12. */
public enum LinkedSceneType {
    FALSE_GUIDE("false_guide", ManifestationKind.ABSENT, ManifestationKind.RAMPANT),
    SKITTER_OMEN("skitter_omen", ManifestationKind.SCUTTLER, ManifestationKind.RAMPANT),
    NEGATIVE_WITNESS("negative_witness", ManifestationKind.PROJECTIONIST,
            ManifestationKind.ORIGINEL);

    private final String id;
    private final ManifestationKind primary;
    private final ManifestationKind followUp;

    LinkedSceneType(String id, ManifestationKind primary, ManifestationKind followUp) {
        this.id = id;
        this.primary = primary;
        this.followUp = followUp;
    }

    public String id() {
        return id;
    }

    public ManifestationKind primary() {
        return primary;
    }

    public ManifestationKind followUp() {
        return followUp;
    }

    public static LinkedSceneType byId(String id) {
        if (id == null) {
            return null;
        }
        return Arrays.stream(values()).filter(value -> value.id.equals(id)).findFirst().orElse(null);
    }
}
