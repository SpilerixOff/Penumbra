package fr.penumbra.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import fr.penumbra.badge.PenumbraBadges;
import fr.penumbra.config.PenumbraConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestionnaire de la progression narrative « Quelque chose apprend à être vous » :
 * stade d'histoire (0–6, voir {@link StoryStage}) et compteurs de découvertes,
 * persistés PAR JOUEUR dans les données du monde (même pattern SavedData + Codec
 * que {@code PenumbraPersistentState} / {@code PlayerHorrorProfile} : clés UUID
 * sérialisées en chaînes, champs optionnels avec défauts, {@code setDirty()}
 * systématique après mutation).
 * <p>Rétrocompatibilité : toute donnée absente (monde antérieur, champ corrompu)
 * retombe sur le stade 0 et des compteurs vides au lieu d'invalider l'état.</p>
 * <p>Tout est événementiel : aucune boucle par tick, aucune allocation par tick.</p>
 */
public class StoryManager extends SavedData {

    /** Nom du fichier de données dans le répertoire du monde. */
    private static final String DATA_NAME = "penumbra_story";

    // ——— Clés contractuelles de découvertes (noteDiscovery) ———
    /** Première anomalie remarquée par le joueur (règle 0→1). */
    public static final String KEY_FIRST_ANOMALY = "first_anomaly";
    /** Observation du poste d'observation — structure (règle 0→1, alternative). */
    public static final String KEY_STRUCTURE_OBSERVATION_POST = "structure_observation_post";
    /** Événement d'imitation subi (règle 1→2, ×{@value #MIMIC_EVENTS_REQUIRED}). */
    public static final String KEY_MIMIC_EVENT = "mimic_event";
    /** Hôte aperçu dans ou près du refuge (règle 2→3, ×{@value #REFUGE_HOST_SEEN_REQUIRED}). */
    public static final String KEY_REFUGE_HOST_SEEN = "refuge_host_seen";
    /** Nuit passée dans un lit (règle 2→3, ×{@value #BED_USED_REQUIRED}). */
    public static final String KEY_BED_USED = "bed_used";
    /** Copie imparfaite du refuge découverte (règle 3→4, ×{@value #COPY_BUILT_REQUIRED}). */
    public static final String KEY_COPY_BUILT = "copy_built";
    /** Hôte observé — jumelles ou à l'œil nu (règle 4→5, ×{@value #HOST_OBSERVED_REQUIRED}). */
    public static final String KEY_HOST_OBSERVED = "host_observed";
    /** Rituel du Sanctuaire de cendre accompli (règle 5→6). */
    public static final String KEY_SANCTUM_RITUAL = "sanctum_ritual";
    /** Contamination progressive d'un refuge (compteur d'enquête, hors seuils 0–6). */
    public static final String KEY_REFUGE_CONTAMINATION = "refuge_contamination";
    /** Scène coordonnée entre deux manifestations (compteur d'enquête). */
    public static final String KEY_LINKED_SCENE = "linked_scene";
    /** Faux compagnon perçu et vérifié (compteur d'enquête, hors seuils 0–6). */
    public static final String KEY_FALSE_COMPANION = "false_companion";

    // ——— Seuils contractuels de progression ———
    private static final int MIMIC_EVENTS_REQUIRED = 3;
    private static final int REFUGE_HOST_SEEN_REQUIRED = 1;
    private static final int BED_USED_REQUIRED = 2;
    private static final int COPY_BUILT_REQUIRED = 1;
    private static final int HOST_OBSERVED_REQUIRED = 4;

    /**
     * Données narratives d'un joueur : stade (0–6) et compteurs de découvertes.
     * Codec à champs OPTIONNELS avec défauts : toute donnée absente retombe sur
     * le stade 0 et des compteurs vides (rétrocompatibilité).
     */
    public static final class PlayerStory {

        /** Codec complet des données d'un joueur. */
        public static final Codec<PlayerStory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("stage", 0).forGetter(PlayerStory::stage),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counters", Map.of())
                        .forGetter(story -> Map.copyOf(story.counters))
        ).apply(instance, PlayerStory::new));

        private int stage;
        private final Map<String, Integer> counters;

        /** Données vierges (nouveau joueur) : stade 0, aucun compteur. */
        public PlayerStory() {
            this(0, Map.of());
        }

        /** Constructeur utilisé par le Codec (valeurs déjà défautées par optionalFieldOf). */
        private PlayerStory(int stage, Map<String, Integer> counters) {
            this.stage = Mth.clamp(stage, 0, StoryStage.MAX_INDEX);
            this.counters = new HashMap<>(counters);
        }

        /** Stade narratif actuel (0–6). */
        public int stage() {
            return this.stage;
        }

        /** Valeur du compteur d'une découverte (0 si jamais notée). */
        public int count(String key) {
            return this.counters.getOrDefault(key, 0);
        }

        /** Vue immuable des compteurs de découvertes. */
        public Map<String, Integer> counters() {
            return Map.copyOf(this.counters);
        }

        /** Fixe le stade (valeur déjà bornée par l'appelant). */
        private void stage(int stage) {
            this.stage = stage;
        }

        /** Incrémente le compteur d'une découverte. */
        private void increment(String key) {
            this.counters.merge(key, 1, Integer::sum);
        }
    }

    /** Codec complet de l'état (clés UUID sérialisées en chaînes). */
    public static final Codec<StoryManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), PlayerStory.CODEC)
                    .optionalFieldOf("players", Map.of()).forGetter(state -> state.players)
    ).apply(instance, StoryManager::new));

    // Persistance 26.1 (vérifié dans les sources 26.1.2, même pattern que
    // PenumbraPersistentState) : SavedDataType est un record top-level
    // (Identifier, Supplier, Codec, DataFixTypes) ; DataFixTypes peut être null
    // pour un mod. La (dé)sérialisation est entièrement pilotée par le codec.
    private static final SavedDataType<StoryManager> TYPE =
            new SavedDataType<>(PenumbraMod.id(DATA_NAME), StoryManager::new, CODEC, null);

    private final Map<UUID, PlayerStory> players;

    /** État vierge (nouveau monde). */
    public StoryManager() {
        this(Map.of());
    }

    /** Constructeur utilisé par le Codec. */
    private StoryManager(Map<UUID, PlayerStory> players) {
        this.players = new HashMap<>(players);
    }

    /**
     * Point d'entrée appelé par l'intégrateur depuis {@code PenumbraMod.onInitialize}.
     * 26.1 : aucun registre de SavedData n'existe — le {@link SavedDataType} est
     * passé directement à {@code computeIfAbsent} ; cet appel journalise donc
     * simplement l'initialisation du module narratif.
     */
    public static void register() {
        PenumbraMod.LOGGER.info("Penumbra — progression narrative enregistrée (7 stades).");
    }

    /**
     * Stade narratif d'un joueur : 0 si le joueur n'a aucune donnée ou si le mode
     * histoire est désactivé ({@code PenumbraConfig.storyEnabled}). Lecture pure :
     * ne crée jamais de données.
     */
    public static int getStage(ServerPlayer player) {
        if (!PenumbraConfig.get().storyEnabled) {
            return 0;
        }
        PlayerStory story = get(player).players.get(player.getUUID());
        return story == null ? 0 : story.stage();
    }

    /**
     * Vue immuable des compteurs de découvertes d'un joueur (vide si aucune donnée).
     * Lecture brute, NON soumise à {@code storyEnabled} (diagnostic, commandes).
     */
    public static Map<String, Integer> getCounters(ServerPlayer player) {
        PlayerStory story = get(player).players.get(player.getUUID());
        return story == null ? Map.of() : story.counters();
    }

    /**
     * Fixe le stade narratif d'un joueur (borné 0–6), persiste ({@code setDirty})
     * et retourne true si la valeur a changé. À la MONTÉE de stade uniquement,
     * le joueur reçoit le message narratif discret {@code penumbra.story.stage.<id>}
     * (une ligne, italique gris sombre, jamais explicatif) ; les descentes de stade
     * (commande admin) restent muettes.
     */
    public static boolean setStage(ServerPlayer player, int stage) {
        int clamped = Mth.clamp(stage, 0, StoryStage.MAX_INDEX);
        StoryManager data = get(player);
        PlayerStory story = data.story(player.getUUID());
        int previous = story.stage();
        if (previous == clamped) {
            return false;
        }
        story.stage(clamped);
        data.setDirty();
        if (clamped > previous) {
            player.sendSystemMessage(Component
                    .translatable("penumbra.story.stage." + StoryStage.byIndex(clamped).id())
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        // Phase 4 : badge « Il connaît votre nom » à l'atteinte du stade final.
        if (clamped == StoryStage.MAX_INDEX) {
            PenumbraBadges.grant(player, "confrontation");
        }
        return true;
    }

    /** Réinitialise le stade et tous les compteurs narratifs de ce joueur. */
    public static void reset(ServerPlayer player) {
        StoryManager data = get(player);
        if (data.players.remove(player.getUUID()) != null) {
            data.setDirty();
        }
    }

    /**
     * Comptabilise une découverte ({@code key} contractuelle, voir les constantes
     * {@code KEY_*}) puis évalue les règles de progression automatique. L'incrément
     * est inconditionnel (simple comptabilité persistante) ; l'évaluation des règles
     * est soumise à {@code PenumbraConfig.storyEnabled}.
     * <p>Règles (un seul franchissement de stade par appel) :</p>
     * <ul>
     *   <li>0→1 : {@code first_anomaly} ×1 OU {@code structure_observation_post} ×1 ;</li>
     *   <li>1→2 : {@code mimic_event} ×{@value #MIMIC_EVENTS_REQUIRED} ;</li>
     *   <li>2→3 : {@code refuge_host_seen} ×{@value #REFUGE_HOST_SEEN_REQUIRED}
     *       ET {@code bed_used} ×{@value #BED_USED_REQUIRED} ;</li>
     *   <li>3→4 : {@code copy_built} ×{@value #COPY_BUILT_REQUIRED} ;</li>
     *   <li>4→5 : {@code host_observed} ×{@value #HOST_OBSERVED_REQUIRED} ;</li>
     *   <li>5→6 : {@code sanctum_ritual} ×1.</li>
     * </ul>
     */
    public static void noteDiscovery(ServerPlayer player, String key) {
        StoryManager data = get(player);
        PlayerStory story = data.story(player.getUUID());
        story.increment(key);
        data.setDirty();
        if (PenumbraConfig.get().storyEnabled) {
            evaluateProgression(player, story);
        }
    }

    /**
     * Évalue la règle du stade courant et fait monter le joueur d'UN stade si elle
     * est remplie (via {@link #setStage}, donc message narratif + persistance).
     * Les compteurs étant monotones, les seuils déjà atteints se réévaluent sans
     * coût à chaque découverte suivante.
     */
    private static void evaluateProgression(ServerPlayer player, PlayerStory story) {
        int target = switch (StoryStage.byIndex(story.stage())) {
            case NORMALCY -> story.count(KEY_FIRST_ANOMALY) >= 1
                    || story.count(KEY_STRUCTURE_OBSERVATION_POST) >= 1 ? 1 : 0;
            case TRACES -> story.count(KEY_MIMIC_EVENT) >= MIMIC_EVENTS_REQUIRED ? 2 : 1;
            case IMITATION -> story.count(KEY_REFUGE_HOST_SEEN) >= REFUGE_HOST_SEEN_REQUIRED
                    && story.count(KEY_BED_USED) >= BED_USED_REQUIRED ? 3 : 2;
            case FAMILIARITY -> story.count(KEY_COPY_BUILT) >= COPY_BUILT_REQUIRED ? 4 : 3;
            case SUBSTITUTION -> story.count(KEY_HOST_OBSERVED) >= HOST_OBSERVED_REQUIRED ? 5 : 4;
            case RECOGNITION -> story.count(KEY_SANCTUM_RITUAL) >= 1 ? 6 : 5;
            case CONFRONTATION -> StoryStage.MAX_INDEX;
        };
        if (target > story.stage()) {
            setStage(player, target);
        }
    }

    /** Récupère l'état persistant du monde, en le créant vierge si nécessaire. */
    private static StoryManager get(ServerPlayer player) {
        // 26.1 : SavedDataStorage.computeIfAbsent(SavedDataType) — un seul argument.
        // ServerLevel.getServer() est non null pour un ServerPlayer (vérifié javap 26.1.2).
        return ((ServerLevel) player.level()).getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Données narratives d'un joueur ; créées vierges (et marquées sales) si absentes. */
    private PlayerStory story(UUID id) {
        PlayerStory story = this.players.get(id);
        if (story == null) {
            story = new PlayerStory();
            this.players.put(id, story);
            setDirty();
        }
        return story;
    }
}
