package fr.penumbra.story;

import net.minecraft.util.Mth;

/**
 * Les sept stades de la progression narrative de Penumbra — « Quelque chose
 * apprend à être vous ». Chaque stade possède un id chaîne contractuel
 * (persistance et clés de traduction {@code penumbra.story.stage.<id>}) et un
 * index numérique 0–6 (API {@link StoryManager}, commande {@code /penumbra story}).
 * L'ordre de déclaration correspond aux index : ne jamais réordonner les constantes.
 */
public enum StoryStage {

    /** Normalité : rien n'a encore été remarqué. */
    NORMALCY(0, "normalcy"),
    /** Traces : les choses ne sont plus tout à fait à leur place. */
    TRACES(1, "traces"),
    /** Imitation : quelque chose répète les gestes du joueur. */
    IMITATION(2, "imitation"),
    /** Familiarité : l'Hôte connaît les habitudes et le refuge du joueur. */
    FAMILIARITY(3, "familiarity"),
    /** Substitution : quelque chose se tient à la place du joueur. */
    SUBSTITUTION(4, "substitution"),
    /** Reconnaissance : le joueur reconnaît l'Hôte ; l'Hôte le reconnaissait depuis longtemps. */
    RECOGNITION(5, "recognition"),
    /** Confrontation : il n'y a plus de place pour deux. */
    CONFRONTATION(6, "confrontation");

    /** Index du dernier stade (borne haute de toute progression). */
    public static final int MAX_INDEX = CONFRONTATION.index;

    private final int index;
    private final String id;

    StoryStage(int index, String id) {
        this.index = index;
        this.id = id;
    }

    /** Index numérique contractuel du stade (0–6), aligné sur l'ordre de déclaration. */
    public int index() {
        return this.index;
    }

    /** Id chaîne contractuel (persistance, clés de traduction). */
    public String id() {
        return this.id;
    }

    /** Stade portant cet id, ou {@link #NORMALCY} si l'id est inconnu (données corrompues). */
    public static StoryStage fromId(String id) {
        for (StoryStage stage : values()) {
            if (stage.id.equals(id)) {
                return stage;
            }
        }
        return NORMALCY;
    }

    /** Stade d'index donné, borné entre {@link #NORMALCY} et {@link #CONFRONTATION}. */
    public static StoryStage byIndex(int index) {
        return values()[Mth.clamp(index, 0, MAX_INDEX)];
    }

    /** Stade suivant ; {@link #CONFRONTATION} demeure {@link #CONFRONTATION} (dernier stade). */
    public StoryStage next() {
        return byIndex(this.index + 1);
    }
}
