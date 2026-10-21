package fr.penumbra.refuge;

import java.util.List;

/**
 * Modèle pur de la contamination progressive d'un refuge.
 *
 * <p>Cette classe ne lit ni le monde, ni un joueur, ni la configuration. Elle
 * centralise les transitions 0–4, le choix d'effet, les cooldowns et la
 * décision de nettoyage compare-and-set. Le gestionnaire serveur peut donc
 * rester une couche d'effets, tandis que les règles importantes sont testables
 * sans lancer Minecraft.</p>
 */
public final class RefugeContaminationModel {

    /** Niveau persistant de contamination, toujours borné entre 0 et 4. */
    public enum Stage {
        DORMANT(0),
        MARKED(1),
        DISTURBED(2),
        INFILTRATED(3),
        IMPOSSIBLE(4);

        private final int level;

        Stage(int level) {
            this.level = level;
        }

        public int level() {
            return this.level;
        }

        public static Stage byLevel(int level) {
            return values()[Math.max(0, Math.min(values().length - 1, level))];
        }

        public Stage next() {
            return byLevel(this.level + 1);
        }
    }

    /**
     * Audience explicite d'une manifestation. Les effets avec entités
     * visuelles ne sont produits que pour {@link #LOCAL_SHARED}; une scène
     * {@link #OWNER_ONLY} utilise uniquement paquets et particules ciblés.
     */
    public enum Audience {
        OWNER_ONLY("owner_only", 0.0),
        LOCAL_SHARED("local_shared", 24.0);

        private final String id;
        private final double radius;

        Audience(String id, double radius) {
            this.id = id;
            this.radius = radius;
        }

        public String id() {
            return this.id;
        }

        public double radius() {
            return this.radius;
        }

        public static Audience byId(String id) {
            if (id != null) {
                for (Audience audience : values()) {
                    if (audience.id.equalsIgnoreCase(id)) {
                        return audience;
                    }
                }
            }
            return OWNER_ONLY;
        }
    }

    /** Manifestations sûres : aucune ne déplace un vrai bloc ou ItemStack. */
    public enum Effect {
        WHISPER,
        ASH_TRACES,
        MOVED_OBJECT_ECHO,
        RED_TORCH_ECHO,
        ABANDONED_PHOTO,
        FALSE_CHEST,
        IMPOSSIBLE_ROOM
    }

    /** État compact persistable par refuge et par propriétaire. */
    public record State(Stage stage, long lastEventTick, long nextEligibleTick,
                        int successfulEvents, int eventsAtStage) {

        public State {
            stage = stage == null ? Stage.DORMANT : stage;
            lastEventTick = Math.max(0L, lastEventTick);
            nextEligibleTick = Math.max(0L, nextEligibleTick);
            successfulEvents = Math.max(0, successfulEvents);
            eventsAtStage = Math.max(0, eventsAtStage);
        }

        public static State initial() {
            return new State(Stage.DORMANT, 0L, 0L, 0, 0);
        }
    }

    /** Résolution sûre d'une manifestation temporaire arrivée à terme. */
    public enum RestorationAction {
        /** Le chunk n'est pas chargé : conserver le suivi, sans le charger. */
        WAIT_FOR_LOADED_CHUNK,
        /** L'entité attendue et son jeton correspondent : elle peut être retirée. */
        REMOVE_MATCHING_VISUAL,
        /** L'entité n'existe déjà plus : oublier seulement le suivi. */
        FORGET_MISSING_VISUAL,
        /** UUID présent mais jeton/type différent : ne jamais toucher cette entité. */
        LEAVE_CHANGED_VISUAL
    }

    private static final List<Effect> DORMANT_EFFECTS = List.of(Effect.WHISPER);
    private static final List<Effect> MARKED_EFFECTS = List.of(
            Effect.ASH_TRACES, Effect.MOVED_OBJECT_ECHO);
    private static final List<Effect> DISTURBED_EFFECTS = List.of(
            Effect.RED_TORCH_ECHO, Effect.ABANDONED_PHOTO);
    private static final List<Effect> INFILTRATED_EFFECTS = List.of(Effect.FALSE_CHEST);
    private static final List<Effect> IMPOSSIBLE_EFFECTS = List.of(Effect.IMPOSSIBLE_ROOM);

    private RefugeContaminationModel() {
    }

    /**
     * Un déclenchement naturel exige le propriétaire proche, tous les chunks
     * du petit volume chargés, le cooldown expiré et un budget visuel libre.
     */
    public static boolean canTrigger(State state, long now, boolean ownerNear,
                                     boolean areaLoaded, boolean visualBudgetAvailable) {
        State safe = state == null ? State.initial() : state;
        return now >= 0L && ownerNear && areaLoaded && visualBudgetAvailable
                && now >= safe.nextEligibleTick();
    }

    /** Choix déterministe et alterné parmi les effets permis par le niveau. */
    public static Effect chooseEffect(State state) {
        State safe = state == null ? State.initial() : state;
        List<Effect> effects = effectsFor(safe.stage());
        return effects.get(Math.floorMod(safe.eventsAtStage(), effects.size()));
    }

    public static List<Effect> effectsFor(Stage stage) {
        Stage safe = stage == null ? Stage.DORMANT : stage;
        return switch (safe) {
            case DORMANT -> DORMANT_EFFECTS;
            case MARKED -> MARKED_EFFECTS;
            case DISTURBED -> DISTURBED_EFFECTS;
            case INFILTRATED -> INFILTRATED_EFFECTS;
            case IMPOSSIBLE -> IMPOSSIBLE_EFFECTS;
        };
    }

    /**
     * Valide un effet réussi : progression monotone jusqu'à 4 et cooldown
     * croissant. Le niveau 4 reste rejouable, mais nettement plus espacé.
     */
    public static State afterSuccessfulEffect(State state, long now) {
        return afterSuccessfulEffect(state, now, Stage.IMPOSSIBLE.level(), 0L);
    }

    /** Variante configurée : plafond de progression et cooldown minimal serveur. */
    public static State afterSuccessfulEffect(State state, long now,
                                              int maximumStage,
                                              long minimumCooldownTicks) {
        State safe = state == null ? State.initial() : state;
        long safeNow = Math.max(0L, now);
        long next = saturatingAdd(safeNow, Math.max(
                cooldownTicks(safe.stage()), Math.max(0L, minimumCooldownTicks)));
        int atStage = safe.eventsAtStage() == Integer.MAX_VALUE
                ? Integer.MAX_VALUE : safe.eventsAtStage() + 1;
        boolean advance = safe.stage() != Stage.IMPOSSIBLE
                && safe.stage().level() < Math.max(0, Math.min(4, maximumStage))
                && atStage >= effectsFor(safe.stage()).size();
        Stage nextStage = advance ? safe.stage().next() : safe.stage();
        return new State(nextStage, safeNow, next,
                safe.successfulEvents() == Integer.MAX_VALUE
                        ? Integer.MAX_VALUE : safe.successfulEvents() + 1,
                advance ? 0 : atStage);
    }

    public static long cooldownTicks(Stage stage) {
        Stage safe = stage == null ? Stage.DORMANT : stage;
        return switch (safe) {
            case DORMANT -> 1_200L;
            case MARKED -> 2_400L;
            case DISTURBED -> 3_600L;
            case INFILTRATED -> 6_000L;
            case IMPOSSIBLE -> 12_000L;
        };
    }

    /** Compare-and-set pur utilisé au nettoyage des entités visuelles. */
    public static RestorationAction restorationAction(boolean chunkLoaded,
                                                       boolean entityPresent,
                                                       boolean displayEntity,
                                                       boolean tokenMatches) {
        if (!chunkLoaded) {
            return RestorationAction.WAIT_FOR_LOADED_CHUNK;
        }
        if (!entityPresent) {
            return RestorationAction.FORGET_MISSING_VISUAL;
        }
        if (displayEntity && tokenMatches) {
            return RestorationAction.REMOVE_MATCHING_VISUAL;
        }
        return RestorationAction.LEAVE_CHANGED_VISUAL;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
