package fr.penumbra.photo;

import com.mojang.serialization.Codec;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Catégorie stable d'une anomalie réellement cadrée sur un négatif. */
public enum PhotoEvidence {
    NONE("none", 0),
    HOST("host", 50),
    WATCHER("watcher", 60),
    ORIGINEL("originel", 100),
    CONTREFAIT("contrefait", 70),
    PROJECTIONIST("projectionist", 85),
    RAMPANT("rampant", 80),
    ABSENT("absent", 90),
    SCUTTLER("scuttler", 40),
    /** 0.15.0 : créature vanilla possédée cadrée avant sa dissolution. */
    FAUX_SEMBLANT("faux_semblant", 45),
    LISTENER("listener", 75);

    public static final int MAX_ID_LENGTH = 24;
    public static final int MAX_PER_PHOTO = 8;
    public static final Codec<PhotoEvidence> CODEC = Codec.STRING.xmap(
            PhotoEvidence::byId, PhotoEvidence::id);

    private final String id;
    private final int priority;

    PhotoEvidence(String id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    public String id() {
        return id;
    }

    public int priority() {
        return priority;
    }

    public boolean discoverable() {
        return this != NONE;
    }

    public String translationKey() {
        return "evidence.penumbra." + id;
    }

    public static PhotoEvidence byId(String id) {
        if (id == null || id.isBlank()) {
            return NONE;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.id.equals(normalized))
                .findFirst().orElse(NONE);
    }

    public static int discoverableCount() {
        return values().length - 1;
    }

    /** Le dossier peut conserver toutes les catégories, indépendamment du quota d'un cliché. */
    public static List<PhotoEvidence> normalizeDossier(Collection<PhotoEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<PhotoEvidence> unique = new LinkedHashSet<>();
        for (PhotoEvidence value : evidence) {
            if (value != null && value.discoverable()) {
                unique.add(value);
            }
        }
        return List.copyOf(unique);
    }

    /** Les identifiants futurs ou inconnus ne deviennent jamais des découvertes. */
    public static List<PhotoEvidence> dossierFromIds(Collection<String> ids) {
        return ids == null ? List.of() : normalizeDossier(ids.stream()
                .map(PhotoEvidence::byId).toList());
    }

    /** Paliers du dossier : première preuve, puis 3, 5 et 7 catégories uniques. */
    public static int dossierStage(int discoveredCount) {
        int count = Math.max(0, discoveredCount);
        if (count >= 7) {
            return 4;
        }
        if (count >= 5) {
            return 3;
        }
        if (count >= 3) {
            return 2;
        }
        return count >= 1 ? 1 : 0;
    }
}
