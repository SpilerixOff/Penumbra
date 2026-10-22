package fr.penumbra.director;

import fr.penumbra.PenumbraMod;
import fr.penumbra.badge.PenumbraBadges;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.copy.CopyManager;
import fr.penumbra.copy.CopyTracker;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.HorrorEvents;
import fr.penumbra.event.underground.AbstractUndergroundEvent;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.refuge.RefugeContaminationManager;
import fr.penumbra.refuge.RefugeContaminationModel;
import fr.penumbra.story.StoryManager;
import fr.penumbra.story.ConfrontationManager;
import fr.penumbra.story.StoryStage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Directeur d'horreur de Penumbra : orchestre la courbe de tension et le
 * déclenchement des événements pour chaque joueur.
 * <ul>
 *   <li>tracking comportemental chaque tick, decay de tension toutes les 20 ticks
 *       (0,03/s), sélection d'événement toutes les 100 ticks ;</li>
 *   <li>période de grâce configurable avant le premier événement d'un joueur ;</li>
 *   <li>sélection pondérée (poids × eventFrequency × modulateur d'état) avec
 *       anti-répétition (deque de 6) et cooldown individuel par événement ;</li>
 *   <li>faux calme : entrée 600 ticks après deux manifestations réussies en CRISIS ;</li>
 *   <li>multiplicateurs {@link PenumbraConfig} : horrorIntensity (gain de tension),
 *       eventFrequency (fréquence et poids).</li>
 * </ul>
 * Phase 2 (SPEC2 §4) :
 * <ul>
 *   <li>poursuites ({@link #startChase}/{@link #endChase}) avec stinger,
 *       avertissement contextuel exigé et cooldown global post-poursuite
 *       anti-frustration ({@code postChaseCooldownTicks}) ;</li>
 *   <li>7 réactions jumelles pondérées par état, cooldown 2400 ticks,
 *       anti-répétition {@code wasRecent("bino_<type>")} ;</li>
 *   <li>jumpscares retardés armés par les {@code note*} : délai 100–400 ticks,
 *       probabilité pondérée par la tension, cooldown 3000 ticks par type,
 *       anti-doublon 800 ticks, respect de {@code delayedJumpscaresEnabled}.</li>
 * </ul>
 * Aucune barre ni indicateur de tension n'est jamais affiché au joueur :
 * l'horreur reste entièrement implicite.
 */
public final class HorrorDirector {

    /**
     * Décroissance de tension par seconde. À 0,03/s, un événement CALM (+6)
     * laisse encore 3,3 points après le gap maximal normal de 90 s : la courbe
     * peut enfin monter naturellement au lieu de retomber à zéro entre chaque scène.
     */
    static final float TENSION_DECAY_PER_SECOND = 0.03f;
    /** Cadence de traitement des profils (1 s). */
    private static final int PROFILE_INTERVAL_TICKS = 20;
    /** Cadence de sélection des événements (5 s). */
    private static final int SELECTION_INTERVAL_TICKS = 100;
    /** Repos court après dégâts, manque d'air ou autre vulnérabilité immédiate. */
    private static final Map<UUID, Long> VULNERABILITY_COOLDOWN_UNTIL = new HashMap<>();
    /** Délai après la seconde manifestation de crise avant l'entrée en faux calme (30 s). */
    private static final int FALSE_CALM_DELAY_TICKS = 600;
    /** Durée du faux calme : 45 secondes, assez marquée sans étouffer la partie. */
    private static final long FALSE_CALM_DURATION_TICKS = 900L;
    /** Deux manifestations physiques de crise sont requises avant un faux calme. */
    private static final int CRISIS_MANIFESTATIONS_BEFORE_FALSE_CALM = 2;
    /** Ruptures sobres autorisées à la sortie du faux calme (une seule sera jouée). */
    private static final List<String> FALSE_CALM_BREAK_EVENTS =
            List.of("breath", "footsteps", "anticlimax", "silence");
    /** Bonus de tension d'un jumpscare majeur (SPEC : +20). */
    private static final float MAJOR_JUMPSCARE_TENSION = 20f;
    /** Événements considérés comme des jumpscares majeurs (sting direct au réveil). */
    private static final Set<String> MAJOR_JUMPSCARE_EVENTS = Set.of("sleep_interrupt");

    // ================= Constantes Phase 2 (SPEC2 §4) =================

    /** Cadence du poll comportemental Phase 2 (réactions armées, mémoire, inventaire). */
    private static final int BEHAVIOR_INTERVAL_TICKS = 5;
    /** Cadence de marquage dirty de l'état persistant (10 s). */
    private static final int PERSIST_INTERVAL_TICKS = 200;

    /** Fenêtre pendant laquelle un sighting compte comme avertissement de poursuite. */
    private static final long CHASE_WARNING_WINDOW_TICKS = 2400L;
    /** Portée de recherche d'un Hôte stalker/observateur valant avertissement. */
    private static final double CHASE_WARNING_HOST_RANGE = 40.0;
    /** Portée de recherche d'un Hôte en poursuite (repli d'endChase). */
    private static final double CHASE_HOST_SEARCH_RANGE = 64.0;
    /** Fausse retraite : l'Hôte en HIDING reste entre 16 et 24 blocs du joueur. */
    private static final double FAKE_RETREAT_MIN_DIST = 16.0;
    /** Fausse retraite : distance au-delà de laquelle l'Hôte est replacé. */
    private static final double FAKE_RETREAT_MAX_DIST = 24.0;
    /** Gain de tension au démarrage d'une poursuite (× horrorIntensity). */
    private static final float CHASE_TENSION_GAIN = 12f;
    /** Intervalle minimal entre deux jets naturels de poursuite (20 s). */
    private static final long NATURAL_CHASE_ROLL_INTERVAL_TICKS = 400L;
    /** Chance par jet en HUNT ; un Hôte avertisseur doit déjà être présent. */
    private static final float NATURAL_CHASE_HUNT_CHANCE = 0.55f;
    /** En CRISIS, une apparition avertisseuse devient toujours une poursuite. */
    private static final float NATURAL_CHASE_CRISIS_CHANCE = 1.00f;

    /** Délai minimal d'un jumpscare retardé (SPEC2 §4 : 100 ticks). */
    private static final int SCARE_MIN_DELAY_TICKS = 100;
    /** Délai maximal d'un jumpscare retardé (SPEC2 §4 : 400 ticks). */
    private static final int SCARE_MAX_DELAY_TICKS = 400;
    /** Cooldown par type de scare (SPEC2 §4 : 3000 ticks). */
    private static final long SCARE_TYPE_COOLDOWN_TICKS = 3000L;
    /** Intervalle minimal entre deux scares, tous types confondus (SPEC2 §4 : 800 ticks). */
    private static final long SCARE_ANY_MIN_GAP_TICKS = 800L;
    /** Chance de base d'un scare retardé (tension 0). */
    private static final float SCARE_BASE_CHANCE = 0.15f;
    /** Bonus de chance d'un scare retardé à tension maximale. */
    private static final float SCARE_TENSION_SCALE = 0.55f;
    /** Gain de tension d'un scare retardé déclenché (× horrorIntensity). */
    private static final float SCARE_TENSION_GAIN = 10f;
    /** Chance de base d'armer un scare à l'ouverture de l'inventaire. */
    private static final float INVENTORY_SCARE_BASE_CHANCE = 0.10f;
    /** Bonus de chance d'armement inventaire à tension maximale. */
    private static final float INVENTORY_SCARE_TENSION_SCALE = 0.50f;

    /** Cooldown global des réactions jumelles (SPEC2 §4 : 2400 ticks). */
    private static final long BINO_REACTION_COOLDOWN_TICKS = 2400L;
    /** Expiration d'une réaction armée en attente de dézoom (30 s). */
    private static final int BINO_ARMED_EXPIRY_TICKS = 600;
    /** Durée de la réaction freeze (SPEC2 §4 : 300 ticks). */
    private static final int BINO_FREEZE_TICKS = 300;
    /** Durée totale de mimic_posture (SPEC2 §4 : 200 ticks). */
    private static final int BINO_MIMIC_TICKS = 200;
    /** Cadence de copie de posture pendant mimic_posture. */
    private static final int BINO_MIMIC_INTERVAL_TICKS = 5;
    /** approach_between ne rapproche jamais l'Hôte à moins de 16 blocs du joueur. */
    private static final double BINO_APPROACH_MIN_PLAYER_DIST = 16.0;
    /** Portée maximale d'acquisition naturelle d'un Hôte aux jumelles. */
    private static final double BINO_OBSERVATION_RANGE = 128.0;
    /** Cône étroit des jumelles : environ 10 degrés autour du centre du regard. */
    private static final double BINO_OBSERVATION_DOT = Math.cos(Math.toRadians(10.0));

    /** Regards arrière en fenêtre glissante déclenchant noteLookedBack (SPEC2 §6 : 5). */
    private static final int LOOKBACK_TRIGGER_COUNT = 5;
    /** Fenêtre glissante des regards arrière (SPEC2 §6 : 600 ticks). */
    private static final long LOOKBACK_WINDOW_TICKS = 600L;
    /** Silence sans événement déclenchant noteLongQuiet (SPEC2 §6 : 6000 ticks). */
    private static final long LONG_QUIET_TICKS = 6000L;
    /** Proximité d'un refuge comptant comme « au refuge » (SPEC2 §6 : 10 blocs). */
    private static final double REFUGE_RETURN_RADIUS = 10.0;
    /** Absence hors refuge requise avant un « retour au refuge » (SPEC2 §6 : 1200 ticks). */
    private static final long REFUGE_ABSENCE_TICKS = 1200L;

    /** Événements trop agressifs pour le cooldown anti-frustration post-poursuite. */
    private static final Set<String> AGGRESSIVE_EVENT_IDS =
            Set.of("distant_sighting", "fake_silhouette", "binoculars_sighting",
                    "tunnel_silhouette", "cave_face", "stolen_name",
                    "refuge_intrusion", "mimic_intrusion",
                    "false_companion",
                    // Le leurre est agressif, mais ne devient une observation qu'au screamer réel.
                    "mining_bait");
    /**
     * Événements dont le succès montre réellement un Hôte au joueur. Seuls ces
     * ids alimentent l'avertissement de poursuite et {@code host_observed}; les
     * overlays et armements sans entité en sont volontairement exclus.
     */
    private static final Set<String> HOST_OBSERVATION_EVENT_IDS = Set.of(
            "distant_sighting", "fake_silhouette", "binoculars_sighting",
            "hill_host", "window_silhouette", "tunnel_silhouette", "behind_other");
    /**
     * Ids des événements du pool qui invoquent un Hôte réel (Phase 4 : plafond
     * d'entités multijoueur appliqué à la sélection du directeur).
     */
    private static final Set<String> HOST_SLOT_EVENT_IDS = Set.of(
            "distant_sighting", "fake_silhouette", "binoculars_sighting",
            "hill_host", "window_silhouette", "tunnel_silhouette",
            // 0.6.0 : événements personnels et screamer invoquant un Hôte réel.
            "binoculars_reveal", "behind_other", "mining_bait");
    /**
     * Événements qui produisent effectivement une apparition observable dès que
     * {@link HorrorEvent#run(ServerPlayer)} réussit. Cette notion est distincte
     * du slot d'Hôte : les overlays ne créent pas d'entité, tandis que
     * {@code mining_bait} ne devient une manifestation que plus tard, si son
     * screamer se referme réellement.
     */
    private static final Set<String> MANIFESTATION_EVENT_IDS = Set.of(
            "distant_sighting", "fake_silhouette", "binoculars_sighting",
            "hill_host", "window_silhouette", "tunnel_silhouette",
            "binoculars_reveal", "behind_other", "watcher_stare", "watcher_group",
            "stolen_name", "refuge_intrusion", "mimic_intrusion", "false_companion",
            // 0.17.0 : la traversée de L'Écouteur crée bien une entité visible.
            "listener_passage");
    /** Replis visuels qui réussissent sans entité ni modification du monde. */
    private static final Set<String> RELIABLE_VISUAL_EVENT_IDS =
            Set.of("refuge_intrusion", "mimic_intrusion");
    /**
     * Scènes dont l'effet principal est sonore. Après la limite configurée,
     * le directeur force un autre langage de peur au lieu d'empiler les bruits.
     */
    private static final Set<String> AUDIO_EVENT_IDS = Set.of(
            "footsteps", "breath", "fake_creeper", "silence", "anticlimax",
            "wall_knocks", "deadend_breath", "pickaxe_echo", "roof_steps",
            "echoed_path", "absent_home", "door_knocks", "paired_echo",
            // 0.17.0 : trois scènes entièrement sonores. Elles doivent compter
            // dans le quota, sinon une montée de tension pourrait n'être qu'une
            // succession de bruits — exactement ce que la dette de manifestation
            // a été écrite pour empêcher.
            "held_breath", "wrong_footfall", "sound_bait");
    /**
     * Scènes automatiques qui doivent se coordonner localement en multijoueur.
     * Les anomalies sonores légères restent personnelles et indépendantes ; les
     * apparitions, événements agressifs et jumpscares majeurs ne se superposent
     * pas au même endroit pendant quelques secondes.
     */
    private static final Set<String> GROUP_COORDINATED_EVENT_IDS = Set.of(
            "distant_sighting", "fake_silhouette", "binoculars_sighting",
            "hill_host", "window_silhouette", "tunnel_silhouette",
            "binoculars_reveal", "behind_other", "watcher_stare", "watcher_group",
            "cave_face", "mining_bait", "sleep_interrupt", "paired_echo", "stolen_name",
            "refuge_intrusion", "mimic_intrusion",
            // 0.17.0 : deux Écouteurs ne traversent pas le même carrefour.
            "listener_passage");
    /** Apparitions physiques fiables à privilégier lorsque le joueur est sous terre. */
    private static final Set<String> UNDERGROUND_MANIFESTATION_EVENT_IDS =
            Set.of("tunnel_silhouette", "mimic_intrusion", "false_companion");
    /** Pool propre aux grottes, favorisé face aux événements génériques. */
    private static final Set<String> UNDERGROUND_EVENT_IDS = Set.of(
            "wall_knocks", "false_vein", "foreign_torch", "deadend_breath",
            "tunnel_silhouette", "cave_face", "pickaxe_echo", "shifted_gallery", "impossible_room",
            "mining_bait");
    /**
     * Rayon du plafond d'entités pour les décisions du directeur (Phase 4) :
     * couvre les apparitions proches (24–40 blocs) avec marge ; les apparitions
     * lointaines (60–100 blocs) gardent leurs exclusions propres (100–120 blocs).
     */
    private static final double DIRECTOR_SPAWN_GUARD_RADIUS = 64.0;
    /** Ids des événements d'imitation comptant pour la règle narrative 1→2 (Phase 3). */
    private static final Set<String> MIMIC_EVENT_IDS = Set.of(
            "footsteps", "fake_silhouette", "lure_door", "paired_echo", "stolen_name",
            "mimic_intrusion", "false_companion");
    /**
     * Stade narratif minimal des manifestations qui doivent être apprises
     * progressivement. Les événements absents de la table restent subtils et
     * disponibles dès NORMALCY. Le forçage administrateur ignore cette table.
     */
    private static final Map<String, Integer> MINIMUM_EVENT_STAGES = Map.ofEntries(
            Map.entry("fake_creeper", 1),
            Map.entry("lure_door", 1),
            Map.entry("false_refuge_light", 1),
            Map.entry("animal_stare", 1),
            Map.entry("foreign_torch", 1),
            Map.entry("false_vein", 1),
            Map.entry("shifted_gallery", 1),
            Map.entry("echoed_path", 1),
            Map.entry("distant_sighting", 1),
            Map.entry("binoculars_sighting", 1),
            Map.entry("hill_host", 1),
            Map.entry("window_silhouette", 1),
            Map.entry("torch_chain", 1),
            Map.entry("absent_home", 2),
            Map.entry("door_knocks", 2),
            Map.entry("binoculars_reveal", 2),
            Map.entry("behind_other", 2),
            Map.entry("paired_echo", 1),
            Map.entry("stolen_name", 3),
            Map.entry("impossible_room", 2),
            Map.entry("watcher_stare", 2),
            Map.entry("sleep_interrupt", 2),
            Map.entry("watcher_group", 3),
            Map.entry("refuge_intrusion", 2),
            Map.entry("mimic_intrusion", 2),
            Map.entry("false_companion", 2),
            Map.entry("mining_bait", 3),
            // 0.16.0 : les Échos exigent que l'imitation soit déjà en place.
            Map.entry("action_echo", 3),
            Map.entry("false_rain", 3));
    /** Les 7 types de réactions jumelles (SPEC2 §4), ordre stable pour le tirage pondéré. */
    private static final List<String> BINO_REACTION_TYPES = List.of(
            "freeze", "head_turn", "vanish_on_unzoom", "approach_between",
            "sound_behind", "mimic_posture", "reappear_checked");
    /** Ids des jumpscares retardés (SPEC2 §6), pour /penumbra scare. */
    private static final Set<String> SCARE_IDS = Set.of(
            "chest_scare", "refuge_scare", "torch_scare",
            "lookback_scare", "inventory_scare", "quiet_scare");
    /**
     * Tables de poids des réactions jumelles, indexées par ordinal de
     * {@link TensionState} (CALM, UNEASE, WATCHED, MANIFESTATION, HUNT, CRISIS,
     * FALSE_CALM). Les réactions passives dominent à basse tension, les
     * réactions agressives à haute tension ; jamais d'attaque ni de téléport
     * au contact.
     */
    private static final Map<String, int[]> BINO_WEIGHTS = Map.of(
            "freeze", new int[]{30, 25, 20, 15, 5, 5, 15},
            "head_turn", new int[]{20, 20, 20, 15, 10, 10, 20},
            "vanish_on_unzoom", new int[]{5, 10, 15, 20, 25, 25, 15},
            "approach_between", new int[]{0, 5, 10, 15, 20, 25, 10},
            "sound_behind", new int[]{0, 0, 10, 15, 25, 30, 10},
            "mimic_posture", new int[]{10, 10, 10, 10, 5, 5, 15},
            "reappear_checked", new int[]{5, 10, 15, 15, 15, 15, 20});

    /** Fin du faux calme par joueur (tick de jeu de l'overworld). */
    private static final Map<UUID, Long> FALSE_CALM_UNTIL = new HashMap<>();
    /** Joueurs dont une entrée en faux calme est déjà programmée. */
    private static final Set<UUID> FALSE_CALM_SCHEDULED = new HashSet<>();
    /** Joueurs attendant l'unique rupture contrôlée après leur vrai silence. */
    private static final Set<UUID> FALSE_CALM_BREAK_PENDING = new HashSet<>();
    /** Dernier déclenchement de chaque événement, par joueur (cooldowns individuels). */
    private static final Map<UUID, Map<String, Long>> LAST_EVENT_RUNS = new HashMap<>();
    /** Nombre de scènes audio consécutives, remis à zéro par tout effet visuel/physique. */
    private static final Map<UUID, Integer> CONSECUTIVE_AUDIO_EVENTS = new HashMap<>();

    // ================= État transitoire Phase 2 =================

    /** Fin du cooldown global post-poursuite, par joueur. */
    private static final Map<UUID, Long> GLOBAL_COOLDOWN_UNTIL = new HashMap<>();
    /** Poursuite active : joueur → Hôte (renseigné par startChase). */
    private static final Map<UUID, UUID> ACTIVE_CHASES = new HashMap<>();
    /** Dernier jet d'escalade naturelle vers une poursuite, par joueur. */
    private static final Map<UUID, Long> LAST_NATURAL_CHASE_ROLL = new HashMap<>();
    /** Dernière observation effective d'un Hôte, y compris une scène différée. */
    private static final Map<UUID, Long> LAST_HOST_OBSERVATION_TICK = new HashMap<>();
    /** Dernier déclenchement d'une réaction jumelle, par joueur (cooldown 2400t). */
    private static final Map<UUID, Long> LAST_BINO_REACTION = new HashMap<>();
    /** Réaction jumelle armée en attente du dézoom du joueur. */
    private static final Map<UUID, ArmedBinoReaction> ARMED_BINO_REACTIONS = new HashMap<>();
    /** Hôte déjà acquis pendant la session de zoom courante (un seul jet par cible). */
    private static final Map<UUID, UUID> LAST_BINO_OBSERVED_HOST = new HashMap<>();
    /** Dernier déclenchement effectif de chaque type de scare, par joueur (cooldown 3000t). */
    private static final Map<UUID, Map<String, Long>> SCARE_TYPE_LAST = new HashMap<>();
    /** Types de scare actuellement armés (un seul déclencheur par type à la fois). */
    /** Type -> jeton unique, afin qu'une ancienne tâche ne puisse jamais consommer un réarmement. */
    private static final Map<UUID, Map<String, Long>> ARMED_SCARES = new HashMap<>();
    private static long nextScareArmToken = 1L;
    /** Dernier scare déclenché, tous types confondus (anti-doublon 800t). */
    private static final Map<UUID, Long> LAST_SCARE_ANY = new HashMap<>();
    /** Dernier compteur de regards arrière observé (détection par delta). */
    private static final Map<UUID, Integer> LAST_LOOKBACK_COUNTS = new HashMap<>();
    /** Horodatages des regards arrière récents (fenêtre glissante 600t). */
    private static final Map<UUID, Deque<Long>> LOOKBACK_TIMES = new HashMap<>();
    /** Dernier tick où le joueur était à proximité d'un refuge. */
    private static final Map<UUID, Long> LAST_NEAR_REFUGE = new HashMap<>();
    /** Dernier état de sommeil observé (détection du réveil). */
    private static final Map<UUID, Boolean> WAS_SLEEPING = new HashMap<>();
    /** Dernier état d'inventaire observé (détection de l'ouverture). */
    private static final Map<UUID, Boolean> WAS_INVENTORY_OPEN = new HashMap<>();
    /** 0.5.0 : dernier ordinal d'état synchronisé au client, par joueur. */
    private static final Map<UUID, Integer> LAST_SYNCED_STATE = new HashMap<>();
    /** Commande de test ayant explicitement terminé la grâce de ce joueur. */
    private static final Set<UUID> GRACE_BYPASSED = new HashSet<>();
    /** Une commande de tension demande une sélection dès le prochain cycle de 5 s. */
    private static final Set<UUID> IMMEDIATE_SELECTION_PENDING = new HashSet<>();
    /** Dernière manifestation physique réussie, pour la garantie anti-série audio. */
    private static final Map<UUID, Long> LAST_MANIFESTATION_TICK = new HashMap<>();
    /** Manifestations physiques vécues depuis l'entrée en CRISIS. */
    private static final Map<UUID, Integer> CRISIS_MANIFESTATION_COUNTS = new HashMap<>();
    /**
     * Arbitre de scènes par instance serveur. L'identité évite qu'un serveur
     * intégré et un serveur de test ouverts dans la même JVM partagent un verrou.
     */
    private static final Map<MinecraftServer, MultiplayerEventPacing> MULTIPLAYER_PACING =
            new IdentityHashMap<>();

    /** Réaction jumelle armée : type, Hôte concerné et expiration (tick de jeu). */
    private record ArmedBinoReaction(String type, UUID hostId, long expiryTick) {
    }

    private HorrorDirector() {
    }

    /**
     * Boucle de tick serveur (enregistrée dans {@code PenumbraMod.onInitialize}) :
     * profils toutes les 20 ticks, sélection toutes les 100 ticks.
     * Le tracking comportemental est échantillonné chaque tick (résolution
     * &lt; 10 ticks requise pour détecter les regards arrière).
     * Phase 2 : poll comportemental toutes les 5 ticks (réactions jumelles
     * armées, regards arrière, refuge, réveil, inventaire), « long silence »
     * à la cadence de sélection, et marquage dirty de l'état toutes les 10 s
     * (les profils mutent en continu ; sans cela les mutations n'étaient
     * jamais persistées sur disque).
     */
    public static void onServerTick(MinecraftServer server) {
        cancelDelayedScaresIfDisabled();
        long tick = server.getTickCount();
        boolean profilePhase = tick % PROFILE_INTERVAL_TICKS == 0;
        boolean selectionPhase = tick % SELECTION_INTERVAL_TICKS == 0;
        boolean behaviorPhase = tick % BEHAVIOR_INTERVAL_TICKS == 0;

        PenumbraPersistentState data = PenumbraPersistentState.get(server);
        if (tick % PERSIST_INTERVAL_TICKS == 0) {
            data.setDirty(); // persistance effective des profils (tension, mémoire…)
        }
        List<ServerPlayer> tickPlayers = selectionPhase
                ? MultiplayerEventPacing.fairOrder(
                        server.getPlayerList().getPlayers(),
                        tick / SELECTION_INTERVAL_TICKS,
                        ServerPlayer::getUUID)
                : server.getPlayerList().getPlayers();
        long gameTime = server.overworld().getGameTime();
        for (ServerPlayer player : tickPlayers) {
            if (!player.isAlive() || isVulnerable(player)) {
                VULNERABILITY_COOLDOWN_UNTIL.put(player.getUUID(),
                        gameTime + DirectorPacingRules.RECOVERY_TICKS);
            }
            if (player.isSpectator() || !player.isAlive()) {
                continue;
            }
            PlayerHorrorProfile profile = data.profile(player.getUUID());
            if (profilePhase) refreshPostRitualCalm(data, profile, gameTime);
            if (ConfrontationManager.isActive(player.getUUID())) {
                // The finale has its own signals and recovery windows. Keep
                // the client synchronized without starting unrelated scenes.
                if (profilePhase || selectionPhase) {
                    syncTensionIfNeeded(player, profile, gameTime, selectionPhase);
                }
                continue;
            }
            profile.tickBehavior(player);
            if (profilePhase) {
                profile.decayTension(TENSION_DECAY_PER_SECOND);
            }
            if (behaviorPhase) {
                tickArmedBinocularReaction(player, gameTime);
                tickNaturalBinocularObservation(player, gameTime);
                tickLookBackTracking(player, profile, gameTime);
                tickRefugeReturn(player, profile, gameTime);
                tickSleepWake(player);
                tickInventoryScare(player, profile);
            }
            if (profilePhase) {
                syncTensionIfNeeded(player, profile, gameTime, false);
            }
            if (selectionPhase) {
                // Découverte réelle d'une copie (et non simple construction), à
                // cadence bornée : une vérification toutes les cinq secondes.
                CopyManager.tickDiscovery(player);
                tickCopyCheck(player);
                tickLongQuiet(player, profile, gameTime);
                tickNaturalChase(player, profile, gameTime);
                trySelectEvent(player, profile, gameTime);
                syncTensionIfNeeded(player, profile, gameTime, true);
                tickCrisisContagion(player, profile, gameTime);
            }
        }
    }

    /** Rayon de la contagion de crise (0.6.0). */
    private static final double CONTAGION_RADIUS = 24.0;

    /**
     * 0.6.0 : peur contagieuse — quand un joueur est en CRISIS, les AUTRES
     * joueurs à ≤ {@value #CONTAGION_RADIUS} blocs entendent sa nappe haute,
     * très faiblement (0.15), positionnée sur lui. On ne sait pas ce qu'il
     * vit ; on sait que quelque chose se passe. Toutes les 100 ticks,
     * uniquement si {@code multiplayerContent}.
     */
    private static void tickCrisisContagion(ServerPlayer player, PlayerHorrorProfile profile, long gameTime) {
        if (!PenumbraConfig.get().multiplayerContent
                || perceivedState(player.getUUID(), profile, gameTime) != TensionState.CRISIS
                || !(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        for (ServerPlayer other : serverLevel.players()) {
            if (other != player && !other.isSpectator()
                    && other.position().distanceTo(player.position()) <= CONTAGION_RADIUS) {
                PenumbraNetworking.sendSoundDirect(
                        player, other, PenumbraSounds.AMBIENT_BED_HIGH,
                        player.position(), 0.15f, 1.0f);
            }
        }
    }

    /**
     * 0.5.0 : synchronise l'état de tension vers le client pour la couche
     * ambiante — à chaque changement d'état (vérifié toutes les 20 ticks) et
     * périodiquement toutes les 100 ticks ({@code force}) pour resynchroniser
     * un client qui aurait manqué un paquet. Coût : une lecture de map par
     * joueur par seconde, un paquet de 5 octets au plus toutes les 5 s.
     */
    private static void syncTensionIfNeeded(ServerPlayer player, PlayerHorrorProfile profile,
                                            long gameTime, boolean force) {
        int ordinal = perceivedState(player.getUUID(), profile, gameTime).ordinal();
        Integer last = LAST_SYNCED_STATE.get(player.getUUID());
        if (force || last == null || last != ordinal) {
            LAST_SYNCED_STATE.put(player.getUUID(), ordinal);
            PenumbraNetworking.sendTensionSync(player, ordinal, profile.tension());
        }
    }

    /**
     * 0.5.0 : purge de tout l'état transitoire d'un joueur à sa déconnexion.
     * Sans cela, les ~15 maps statiques du directeur croissaient sans borne
     * sur un serveur longue durée (fuite lente). Appelé par
     * {@code ServerPlayConnectionEvents.DISCONNECT} (PenumbraMod).
     * La grâce, la tension et la mémoire longue survivent, eux, dans le
     * profil persisté — seul l'éphémère est purgé.
     */
    public static void onPlayerDisconnect(UUID playerId) {
        VULNERABILITY_COOLDOWN_UNTIL.remove(playerId);
        FALSE_CALM_UNTIL.remove(playerId);
        FALSE_CALM_SCHEDULED.remove(playerId);
        FALSE_CALM_BREAK_PENDING.remove(playerId);
        LAST_EVENT_RUNS.remove(playerId);
        CONSECUTIVE_AUDIO_EVENTS.remove(playerId);
        GLOBAL_COOLDOWN_UNTIL.remove(playerId);
        ACTIVE_CHASES.remove(playerId);
        LAST_NATURAL_CHASE_ROLL.remove(playerId);
        LAST_HOST_OBSERVATION_TICK.remove(playerId);
        LAST_BINO_REACTION.remove(playerId);
        ARMED_BINO_REACTIONS.remove(playerId);
        LAST_BINO_OBSERVED_HOST.remove(playerId);
        SCARE_TYPE_LAST.remove(playerId);
        ARMED_SCARES.remove(playerId);
        LAST_SCARE_ANY.remove(playerId);
        LAST_LOOKBACK_COUNTS.remove(playerId);
        LOOKBACK_TIMES.remove(playerId);
        LAST_NEAR_REFUGE.remove(playerId);
        WAS_SLEEPING.remove(playerId);
        WAS_INVENTORY_OPEN.remove(playerId);
        LAST_SYNCED_STATE.remove(playerId);
        GRACE_BYPASSED.remove(playerId);
        IMMEDIATE_SELECTION_PENDING.remove(playerId);
        LAST_MANIFESTATION_TICK.remove(playerId);
        CRISIS_MANIFESTATION_COUNTS.remove(playerId);
    }

    /**
     * Purge l'intégralité de l'état transitoire lorsque le serveur s'arrête.
     * Les profils, la tension et l'histoire restent dans les SavedData ; ces
     * tables statiques ne doivent en revanche jamais contaminer le monde lancé
     * ensuite dans la même JVM (tests, serveur intégré ou redémarrage à chaud).
     */
    public static void onServerStopped(MinecraftServer server) {
        VULNERABILITY_COOLDOWN_UNTIL.clear();
        FALSE_CALM_UNTIL.clear();
        FALSE_CALM_SCHEDULED.clear();
        FALSE_CALM_BREAK_PENDING.clear();
        LAST_EVENT_RUNS.clear();
        CONSECUTIVE_AUDIO_EVENTS.clear();
        GLOBAL_COOLDOWN_UNTIL.clear();
        ACTIVE_CHASES.clear();
        LAST_NATURAL_CHASE_ROLL.clear();
        LAST_HOST_OBSERVATION_TICK.clear();
        LAST_BINO_REACTION.clear();
        ARMED_BINO_REACTIONS.clear();
        LAST_BINO_OBSERVED_HOST.clear();
        SCARE_TYPE_LAST.clear();
        ARMED_SCARES.clear();
        nextScareArmToken = 1L;
        LAST_SCARE_ANY.clear();
        LAST_LOOKBACK_COUNTS.clear();
        LOOKBACK_TIMES.clear();
        LAST_NEAR_REFUGE.clear();
        WAS_SLEEPING.clear();
        WAS_INVENTORY_OPEN.clear();
        LAST_SYNCED_STATE.clear();
        GRACE_BYPASSED.clear();
        IMMEDIATE_SELECTION_PENDING.clear();
        LAST_MANIFESTATION_TICK.clear();
        CRISIS_MANIFESTATION_COUNTS.clear();
        MultiplayerEventPacing pacing = MULTIPLAYER_PACING.remove(server);
        if (pacing != null) {
            pacing.clear();
        }
    }

    /** Tension actuelle d'un joueur (0–100). Crée un profil vierge si inconnu. */
    public static float getTension(MinecraftServer server, UUID playerId) {
        return PenumbraPersistentState.get(server).profile(playerId).tension();
    }

    /** État de tension d'un joueur, faux calme inclus. */
    public static TensionState getState(MinecraftServer server, UUID playerId) {
        PenumbraPersistentState data = PenumbraPersistentState.get(server);
        PlayerHorrorProfile profile = data.profile(playerId);
        long now = server.overworld().getGameTime();
        refreshPostRitualCalm(data, profile, now);
        return perceivedState(playerId, profile, now);
    }

    /** Vrai si la grâce naturelle est écoulée ou a été terminée par une commande. */
    public static boolean isGraceComplete(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (GRACE_BYPASSED.contains(playerId)) {
            return true;
        }
        long gameTime = player.level().getGameTime();
        long start = profile(player).graceStartOrInit(gameTime);
        return gameTime - start >= gracePeriodTicks(
                PenumbraConfig.get().eventGracePeriodSeconds);
    }

    /**
     * Stade effectif pour les contenus hors pool : progression sauvegardée ou
     * déblocage temporaire produit par la tension, le plus élevé des deux.
     */
    public static int effectiveContentStage(ServerPlayer player) {
        if (!PenumbraConfig.get().storyEnabled) {
            return StoryStage.MAX_INDEX;
        }
        TensionState state = currentState(
                player.getUUID(), profile(player).tension(), player.level().getGameTime());
        return Math.max(StoryManager.getStage(player), stageUnlockedByTension(state));
    }

    /** Instantané persistant de l'arc 0.12, utilisé par les commandes de diagnostic. */
    public static DirectorMemory.ArcState getArcState(ServerPlayer player) {
        PlayerHorrorProfile value = profile(player);
        return ensureDirectorMemory(value, player.level().getGameTime()).arc();
    }

    /** Démarre un arc thématique sans attendre la sélection naturelle. */
    public static void forceArc(ServerPlayer player, DirectorMemory.ArcTheme theme) {
        long now = player.level().getGameTime();
        PenumbraPersistentState data = PenumbraPersistentState.get(
                player.level().getServer());
        PlayerHorrorProfile value = data.profile(player.getUUID());
        value.directorMemory(HorrorArcEngine.forceStart(
                ensureDirectorMemory(value, now), now, theme, arcSeed(player)));
        data.setDirty();
        GRACE_BYPASSED.add(player.getUUID());
        IMMEDIATE_SELECTION_PENDING.add(player.getUUID());
    }

    /** Efface uniquement l'arc, sans perdre tension, histoire ni dossier photo. */
    public static void clearArc(ServerPlayer player) {
        long now = player.level().getGameTime();
        PenumbraPersistentState data = PenumbraPersistentState.get(
                player.level().getServer());
        PlayerHorrorProfile value = data.profile(player.getUUID());
        value.directorMemory(HorrorArcEngine.clear(
                ensureDirectorMemory(value, now), now));
        data.setDirty();
    }

    /**
     * Force le déclenchement d'un événement : bypass la période de grâce et les
     * cooldowns. Retourne false si l'id est inconnu ou si {@code canRun} est faux.
     */
    public static boolean forceEvent(ServerPlayer player, String eventId) {
        HorrorEvent event = HorrorEvents.byId(eventId);
        if (event == null) {
            return false;
        }
        PlayerHorrorProfile profile = PenumbraPersistentState.get(player.level().getServer())
                .profile(player.getUUID());
        if (!event.canRun(player, profile)) {
            return false;
        }
        long gameTime = player.level().getGameTime();
        return runEvent(player, profile, event,
                currentState(player.getUUID(), profile.tension(), gameTime), gameTime);
    }

    /**
     * Fixe la tension d'un joueur (bornée 0–100 par le profil). Comme cette
     * méthode est utilisée par la commande administrateur, atteindre UNEASE
     * termine la grâce pour rendre le test observable ; CRISIS annule aussi
     * les silences/cooldowns déjà armés, y compris l'accalmie persistante du rituel.
     */
    public static void setTension(ServerPlayer player, float value) {
        PenumbraPersistentState data = PenumbraPersistentState.get(player.level().getServer());
        PlayerHorrorProfile profile = data.profile(player.getUUID());
        profile.addTension(value - profile.tension());
        UUID playerId = player.getUUID();
        if (value >= TensionState.UNEASE.threshold) {
            GRACE_BYPASSED.add(playerId);
            IMMEDIATE_SELECTION_PENDING.add(playerId);
        }
        if (value >= TensionState.CRISIS.threshold) {
            FALSE_CALM_UNTIL.remove(playerId);
            FALSE_CALM_SCHEDULED.remove(playerId); // invalide aussi la tâche différée
            FALSE_CALM_BREAK_PENDING.remove(playerId);
            profile.clearPostRitualCalm();
            GLOBAL_COOLDOWN_UNTIL.remove(playerId);
            CRISIS_MANIFESTATION_COUNTS.remove(playerId);
            long now = player.level().getGameTime();
            long deadline = (long) PenumbraConfig.get().manifestationMaxGapSeconds * 20L;
            LAST_MANIFESTATION_TICK.put(playerId, now - Math.max(1L, deadline));
            profile.directorMemory(ensureDirectorMemory(profile, now)
                    .withLastManifestation(now - Math.max(1L, deadline), now));
        }
        data.setDirty();
    }

    /**
     * Réinitialise l'horreur d'un joueur : profil neuf, nouvelle période de
     * grâce, et remise du mode histoire au stade 0 (demandé par /penumbra reset).
     * Phase 2 : purge aussi tout l'état transitoire (poursuites, réactions
     * jumelles, scares retardés, mémoires glissantes).
     */
    public static void reset(ServerPlayer player) {
        PenumbraPersistentState data = PenumbraPersistentState.get(player.level().getServer());
        UUID playerId = player.getUUID();
        data.resetProfile(playerId);
        VULNERABILITY_COOLDOWN_UNTIL.remove(playerId);
        StoryManager.reset(player);
        CopyTracker.get(player.level()).resetAttempts(playerId);
        RefugeContaminationManager.restoreLoadedForOwner(
                player.level().getServer(), playerId);
        LinkedSceneManager.onPlayerDisconnect(player.level().getServer(), playerId);
        // 0.5.0 : la grâce vit dans le profil ; on la réarme sur le profil neuf.
        data.profile(playerId).restartGrace(player.level().getGameTime());
        FALSE_CALM_UNTIL.remove(playerId);
        FALSE_CALM_SCHEDULED.remove(playerId);
        FALSE_CALM_BREAK_PENDING.remove(playerId);
        LAST_EVENT_RUNS.remove(playerId);
        CONSECUTIVE_AUDIO_EVENTS.remove(playerId);
        // Phase 2 : purge de l'état transitoire.
        GLOBAL_COOLDOWN_UNTIL.remove(playerId);
        ACTIVE_CHASES.remove(playerId);
        LAST_NATURAL_CHASE_ROLL.remove(playerId);
        LAST_HOST_OBSERVATION_TICK.remove(playerId);
        LAST_BINO_REACTION.remove(playerId);
        ARMED_BINO_REACTIONS.remove(playerId);
        LAST_BINO_OBSERVED_HOST.remove(playerId);
        SCARE_TYPE_LAST.remove(playerId);
        ARMED_SCARES.remove(playerId);
        LAST_SCARE_ANY.remove(playerId);
        LAST_LOOKBACK_COUNTS.remove(playerId);
        LOOKBACK_TIMES.remove(playerId);
        LAST_NEAR_REFUGE.remove(playerId);
        WAS_SLEEPING.remove(playerId);
        WAS_INVENTORY_OPEN.remove(playerId);
        LAST_SYNCED_STATE.remove(playerId);
        GRACE_BYPASSED.remove(playerId);
        IMMEDIATE_SELECTION_PENDING.remove(playerId);
        LAST_MANIFESTATION_TICK.remove(playerId);
        CRISIS_MANIFESTATION_COUNTS.remove(playerId);
    }

    /** Délègue une tâche différée au {@link DelayedTaskScheduler}. */
    public static void runLater(ServerLevel level, int delayTicks, Runnable r) {
        DelayedTaskScheduler.runLater(level, delayTicks, r);
    }

    /** Invalide immédiatement les tâches armées lorsque leur option est coupée. */
    public static void cancelDelayedScaresIfDisabled() {
        if (!PenumbraConfig.get().delayedJumpscaresEnabled) {
            ARMED_SCARES.clear();
        }
    }

    /**
     * Enregistre le moment où une apparition est devenue réellement visible.
     * Les événements à effet immédiat passent par {@code runEvent}; les pièges
     * différés, comme {@code mining_bait}, appellent cette méthode au moment
     * précis du screamer afin de ne pas rembourser la dette au simple armement.
     */
    public static void noteManifestation(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        TensionState state = currentState(
                player.getUUID(), profile(player).tension(), gameTime);
        noteManifestation(player, state, gameTime);
    }

    /**
     * Note une observation effective d'un Hôte pour l'histoire et comme
     * avertissement de poursuite. Les armements et overlays ne doivent jamais
     * appeler cette méthode tant qu'aucune entité n'a réellement été montrée.
     */
    public static void noteHostObserved(ServerPlayer player) {
        LAST_HOST_OBSERVATION_TICK.put(player.getUUID(), player.level().getGameTime());
        StoryManager.noteDiscovery(player, StoryManager.KEY_HOST_OBSERVED);
    }

    /** Mise à jour commune de la dette et de la progression de crise. */
    private static void noteManifestation(ServerPlayer player, TensionState state, long gameTime) {
        LAST_MANIFESTATION_TICK.put(player.getUUID(), gameTime);
        PlayerHorrorProfile profile = profile(player);
        DirectorMemory memory = ensureDirectorMemory(profile, gameTime).recordManifestation(gameTime);
        profile.directorMemory(memory);
        noteCrisisManifestation(player, state);
    }

    /** Compte une manifestation effective vers le faux calme de crise. */
    private static void noteCrisisManifestation(ServerPlayer player, TensionState state) {
        UUID playerId = player.getUUID();
        DirectorMemory memory = ensureDirectorMemory(
                profile(player), player.level().getGameTime());
        boolean persistentCalmPending = memory.pacing().falseCalmBreakPending()
                || memory.pending().stream().anyMatch(value -> "false_calm".equals(value.type()));
        if (state == TensionState.CRISIS && !FALSE_CALM_BREAK_PENDING.contains(playerId)
                && !persistentCalmPending) {
            int count = CRISIS_MANIFESTATION_COUNTS.merge(playerId, 1, Integer::sum);
            if (count >= CRISIS_MANIFESTATIONS_BEFORE_FALSE_CALM) {
                CRISIS_MANIFESTATION_COUNTS.remove(playerId);
                notifyFalseCalm(player);
            }
        } else if (state != TensionState.CRISIS) {
            CRISIS_MANIFESTATION_COUNTS.remove(playerId);
        }
    }

    /**
     * Programme l'entrée en FALSE_CALM après l'appel (typiquement
     * juste après un événement en état CRISIS). Le faux calme dure
     * {@value #FALSE_CALM_DURATION_TICKS} ticks puis l'état redevient piloté
     * par la tension.
     */
    public static void notifyFalseCalm(ServerPlayer player) {
        long now = player.level().getGameTime();
        PlayerHorrorProfile profile = profile(player);
        DirectorMemory memory = ensureDirectorMemory(profile, now);
        boolean alreadyPending = memory.arc().phase() == DirectorMemory.ArcPhase.FALSE_CALM
                || memory.pacing().falseCalmBreakPending()
                || memory.pending().stream().anyMatch(value -> "false_calm".equals(value.type()));
        if (alreadyPending) {
            return; // une seule séquence calme/rupture à la fois
        }
        DirectorMemory.PendingConsequence consequence = new DirectorMemory.PendingConsequence(
                "false_calm", now + FALSE_CALM_DELAY_TICKS,
                now + FALSE_CALM_DELAY_TICKS + FALSE_CALM_DURATION_TICKS,
                "crisis", 0);
        profile.directorMemory(memory.scheduleConsequence(consequence));
    }

    // ================= Phase 2 : poursuites (SPEC2 §4) =================

    /**
     * Démarre une poursuite : stinger {@code CHASE_STINGER} à la position de
     * l'Hôte, behavior CHASING, ciblage immédiat du joueur et enregistrement
     * de la paire joueur/Hôte.
     * <p>
     * Volontairement SANS garde-fou anti-frustration : la méthode reste
     * utilisable par {@code /penumbra chase}. L'anti-frustration (cooldown
     * post-poursuite + avertissement contextuel exigé par SPEC2 §4) vit dans
     * le déclencheur automatique : tout déclencheur non administrateur (goal
     * d'IA, événement) DOIT vérifier {@link #canStartChase} avant d'appeler
     * cette méthode. Seul le toggle de contenu {@code chasesEnabled} est
     * respecté ici.
     * </p>
     */
    public static boolean startChase(ServerPlayer player, HostEntity host) {
        if (host == null || !host.isAlive() || !PenumbraConfig.get().chasesEnabled
                || (host.hasOwner() && !host.isOwnedBy(player))) {
            return false;
        }
        UUID playerId = player.getUUID();
        PlayerHorrorProfile profile = profile(player);
        // Stinger de poursuite : prioritaire, hors budget audio ordinaire (Phase 4).
        PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.CHASE_STINGER, host.position(), 1.0f, 1.0f);
        host.setOwner(player);
        host.setMaxLifetimeTicks(1400); // couvre la poursuite maximale + sa mise en scène
        host.setBehavior(HostBehavior.CHASING);
        host.setTarget(player);
        ACTIVE_CHASES.put(playerId, host.getUUID());
        profile.directorMemory(ensureDirectorMemory(profile, player.level().getGameTime())
                .recordIncidental(player.level().getGameTime()));
        profile.addTension(CHASE_TENSION_GAIN * Math.max(0f, PenumbraConfig.get().horrorIntensity));
        debugLog("Poursuite démarrée contre {}", player.getGameProfile().name());
        return true;
    }

    /**
     * Garde-fou anti-frustration du déclencheur automatique de poursuite
     * (SPEC2 §4) : chasses activées, pas de cooldown post-poursuite, et
     * avertissement contextuel présent (sighting récent, Hôte en
     * filature/observation à ≤ 40 blocs, ou tension ≥ HUNT). Tout déclencheur
     * non administrateur (goal d'IA, événement) DOIT vérifier ce prédicat
     * avant d'appeler {@link #startChase} ; {@code /penumbra chase} peut s'en
     * servir pour un feedback honnête.
     * Phase 4 : refuse aussi toute poursuite automatique quand la zone du joueur
     * a atteint le plafond d'Hôtes ({@link #canSpawnHorrorEntityNear}) —
     * {@code /penumbra chase} reste possible (forçage administrateur assumé).
     */
    public static boolean canStartChase(ServerPlayer player) {
        if (!PenumbraConfig.get().chasesEnabled || !automaticEscalationAvailable(player)) {
            return false;
        }
        if (!physicalManifestationAllowed(player)) {
            return false;
        }
        return hasChaseWarning(player, profile(player), player.level().getGameTime());
    }

    /**
     * Les manifestations physiques restent permises en multijoueur car leurs
     * propriétaires, cibles, quotas et commandes sont maintenant isolés. Le
     * réglage {@code multiplayerContent} ne coupe plus les apparitions lorsqu'un
     * ami se trouve à proximité ; il contrôle uniquement les scènes qui utilisent
     * réellement plusieurs joueurs ({@code behind_other}, contagion de crise).
     */
    public static boolean physicalManifestationAllowed(ServerPlayer player) {
        return true;
    }

    /**
     * Plafond multijoueur d'entités d'horreur (Phase 4) : vrai si moins de
     * {@code PenumbraConfig.maxHostEntitiesPerPlayer} Hôtes vivants appartenant
     * au joueur se trouvent à ≤ {@code radius} blocs de lui. Les manifestations
     * des autres joueurs ne consomment donc pas son quota.
     * <p>
     * {@code HostEntity.hasAnyHostNearby} ne convient pas ici : il teste
     * « au moins un », alors que le plafond exige un comptage (1 à 4 selon la
     * config). Coût borné : une seule requête AABB par appel, à réserver aux
     * décisions rares du directeur (sélection d'événement toutes les 100 ticks,
     * garde-fou de poursuite) — jamais à un usage par tick.
     * </p>
     */
    public static boolean canSpawnHorrorEntityNear(ServerPlayer player, double radius) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return true; // hors niveau serveur : comptage impossible, on laisse passer
        }
        int max = Math.max(1, PenumbraConfig.get().maxHostEntitiesPerPlayer);
        AABB box = player.getBoundingBox().inflate(Math.max(0.0, radius));
        // 26.1 : getEntities(EntityTypeTest, AABB, Predicate) remplace getEntitiesOfClass.
        int nearby = serverLevel.getEntities(EntityTypeTest.forClass(HostEntity.class), box,
                (HostEntity host) -> !host.isRemoved() && host.isOwnedBy(player)).size();
        return nearby < max;
    }

    /**
     * Avertissement contextuel exigé avant toute poursuite : dernier événement
     * = sighting (distant_sighting, fake_silhouette, binoculars_sighting) depuis
     * moins de {@value #CHASE_WARNING_WINDOW_TICKS} ticks, ou Hôte en
     * STALKING/OBSERVING à ≤ {@value #CHASE_WARNING_HOST_RANGE} blocs, ou
     * tension ≥ HUNT.
     */
    private static boolean hasChaseWarning(ServerPlayer player, PlayerHorrorProfile profile, long gameTime) {
        if (profile.tension() >= TensionState.HUNT.threshold) {
            return true;
        }
        String lastId = profile.lastEventId();
        if (lastId != null && isHostObservationEvent(lastId)
                && gameTime - profile.lastEventTick() <= CHASE_WARNING_WINDOW_TICKS) {
            return true;
        }
        Long lastObservation = LAST_HOST_OBSERVATION_TICK.get(player.getUUID());
        if (lastObservation != null && gameTime >= lastObservation
                && gameTime - lastObservation <= CHASE_WARNING_WINDOW_TICKS) {
            return true;
        }
        if (player.level() instanceof ServerLevel serverLevel) {
            AABB box = player.getBoundingBox().inflate(CHASE_WARNING_HOST_RANGE);
            // 26.1 : getEntities(EntityTypeTest, AABB, Predicate) remplace getEntitiesOfClass.
            return !serverLevel.getEntities(EntityTypeTest.forClass(HostEntity.class), box,
                    (HostEntity host) -> host.isOwnedBy(player)
                            && (host.getBehavior() == HostBehavior.STALKING
                            || host.getBehavior() == HostBehavior.OBSERVING)).isEmpty();
        }
        return false;
    }

    /**
     * Escalade naturelle et rare d'un Hôte déjà annoncé vers une poursuite. Aucun Hôte
     * n'est créé ici : la scène doit avoir eu le temps d'exister comme avertissement.
     */
    private static void tickNaturalChase(ServerPlayer player, PlayerHorrorProfile profile, long gameTime) {
        TensionState state = currentState(player.getUUID(), profile.tension(), gameTime);
        if (isAutomaticEventPaused(player) || isInFalseCalm(player)
                || !effectiveStageAtLeast(player, state, 4)) {
            return;
        }
        float chance = switch (state) {
            case HUNT -> NATURAL_CHASE_HUNT_CHANCE;
            case CRISIS -> NATURAL_CHASE_CRISIS_CHANCE;
            default -> 0.0f;
        };
        if (chance <= 0.0f || !canStartChase(player)) {
            return;
        }
        HostEntity host = findEscalatableHost(player);
        if (host == null) {
            return;
        }
        Long lastRoll = LAST_NATURAL_CHASE_ROLL.get(player.getUUID());
        if (lastRoll != null && gameTime - lastRoll < NATURAL_CHASE_ROLL_INTERVAL_TICKS) {
            return;
        }
        LAST_NATURAL_CHASE_ROLL.put(player.getUUID(), gameTime);
        if (player.getRandom().nextFloat() < chance) {
            startChase(player, host);
        }
    }

    /** Hôte avertisseur appartenant au joueur, à distance équitable, le plus proche. */
    private static HostEntity findEscalatableHost(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        AABB box = player.getBoundingBox().inflate(CHASE_WARNING_HOST_RANGE);
        HostEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (HostEntity host : level.getEntities(EntityTypeTest.forClass(HostEntity.class), box,
                candidate -> candidate.isAlive() && candidate.isOwnedBy(player)
                        && (candidate.getBehavior() == HostBehavior.STALKING
                        || candidate.getBehavior() == HostBehavior.OBSERVING))) {
            double distance = host.distanceTo(player);
            if (distance >= 8.0 && distance < bestDist) {
                best = host;
                bestDist = distance;
            }
        }
        return best;
    }

    /**
     * Termine une poursuite : arme le cooldown global post-poursuite
     * ({@code postChaseCooldownTicks}) pendant lequel aucun événement agressif
     * (chasse, scare, sighting) ne peut survenir.
     * <p>
     * {@code fakeRetreat} : l'Hôte survit, passe en HIDING et reste à
     * 16–24 blocs (replacé derrière le joueur s'il s'est trop éloigné) ;
     * sinon l'Hôte se dissout en cendre (particules + murmure + discard).
     * </p>
     */
    public static void endChase(ServerPlayer player, boolean fakeRetreat) {
        UUID playerId = player.getUUID();
        long gameTime = player.level().getGameTime();
        int cooldown = Math.max(0, PenumbraConfig.get().postChaseCooldownTicks);
        GLOBAL_COOLDOWN_UNTIL.put(playerId, gameTime + cooldown);

        HostEntity host = null;
        UUID hostId = ACTIVE_CHASES.remove(playerId);
        if (hostId != null && player.level() instanceof ServerLevel serverLevel) {
            // 26.1 : getEntityInAnyDimension(UUID) sur ServerLevel (getEntity(UUID) est par-niveau).
            Entity entity = serverLevel.getEntityInAnyDimension(hostId);
            if (entity instanceof HostEntity found && found.isAlive()) {
                host = found;
            }
        }
        if (host == null) {
            host = findChaseHost(player); // repli : Hôte en poursuite le plus proche
        }
        if (host != null) {
            host.setTarget(null);
            if (fakeRetreat) {
                host.setBehavior(HostBehavior.HIDING);
                if (host.distanceTo(player) > FAKE_RETREAT_MAX_DIST) {
                    teleportBehindPlayer(player, host, FAKE_RETREAT_MIN_DIST, FAKE_RETREAT_MAX_DIST);
                }
            } else {
                vanishInAsh(host);
            }
        }
        debugLog("Poursuite terminée pour {} (fausse retraite : {}, cooldown {}t)",
                player.getGameProfile().name(), fakeRetreat, cooldown);
        // Phase 4 : badge « Encore là » — le joueur a survécu à une poursuite.
        if (player.isAlive()) {
            PenumbraBadges.grant(player, "survivor");
        }
    }

    /** Cooldown post-poursuite ou récompense de calme persistante propre au joueur. */
    public static boolean isInGlobalCooldown(ServerPlayer player) {
        if (isAutomaticEventPaused(player)) return true;
        Long until = GLOBAL_COOLDOWN_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    /** Vrai pendant la fenêtre de silence total FALSE_CALM. */
    private static boolean isInFalseCalm(ServerPlayer player) {
        Long until = FALSE_CALM_UNTIL.get(player.getUUID());
        if (until != null && player.level().getGameTime() < until) {
            return true;
        }
        return ensureDirectorMemory(profile(player), player.level().getGameTime())
                .arc().phase() == DirectorMemory.ArcPhase.FALSE_CALM;
    }

    /** Vrai pendant une pause scénarisée bloquant tout événement automatique. */
    private static boolean isAutomaticEventPaused(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        PenumbraPersistentState data = PenumbraPersistentState.get(server);
        PlayerHorrorProfile profile = data.profile(player.getUUID());
        refreshPostRitualCalm(data, profile, server.overworld().getGameTime());
        return profile.postRitualCalm().active();
    }

    private static void refreshPostRitualCalm(PenumbraPersistentState data,
                                             PlayerHorrorProfile profile, long overworldGameTime) {
        if (profile.advancePostRitualCalm(overworldGameTime)) data.setDirty();
    }

    /**
     * 0.5.0 : accalmie scriptée post-rituel (finale du Sanctuaire de cendre) —
     * tension remise à zéro et silence persisté, borné à 48 000 ticks de jeu
     * de l'Overworld (aucun événement automatique pendant cette fenêtre). L'épilogue n'est pas
     * la fin de l'horreur : à l'issue de l'accalmie, il réapprend.
     */
    public static void applyPostRitualCalm(ServerPlayer player, long calmTicks) {
        MinecraftServer server = player.level().getServer();
        PenumbraPersistentState data = PenumbraPersistentState.get(server);
        PlayerHorrorProfile profile = data.profile(player.getUUID());
        profile.addTension(-profile.tension());
        UUID playerId = player.getUUID();
        profile.extendPostRitualCalm(server.overworld().getGameTime(), calmTicks);
        data.setDirty();
        FALSE_CALM_UNTIL.remove(playerId);
        FALSE_CALM_SCHEDULED.remove(playerId);
        FALSE_CALM_BREAK_PENDING.remove(playerId);
        debugLog("Accalmie post-rituel de {} ticks pour {}", calmTicks, player.getGameProfile().name());
    }

    /** Hôte en CHASING/ATTACKING le plus proche du joueur (≤ 64 blocs), ou null. */
    private static HostEntity findChaseHost(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        AABB box = player.getBoundingBox().inflate(CHASE_HOST_SEARCH_RANGE);
        HostEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (HostEntity host : serverLevel.getEntities(EntityTypeTest.forClass(HostEntity.class), box,
                (HostEntity h) -> h.isOwnedBy(player)
                        && (h.getBehavior() == HostBehavior.CHASING
                        || h.getBehavior() == HostBehavior.ATTACKING))) {
            double dist = host.distanceTo(player);
            if (dist < bestDist) {
                best = host;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Dissolution en cendre de l'Hôte (pattern de VanishWhenSeenGoal) :
     * particules ASH_MOTE, HOST_WHISPER audible par les joueurs proches, puis discard.
     */
    private static void vanishInAsh(HostEntity host) {
        if (host.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(PenumbraParticles.ASH_MOTE,
                    host.getX(), host.getY() + 1.3, host.getZ(), 24, 0.4, 0.8, 0.4, 0.01);
            Vec3 pos = host.position();
            float pitch = 0.9f + host.getRandom().nextFloat() * 0.2f;
            // 0.5.0 : variante de murmure (improvedSoundsEnabled) — la familiarité tue la peur.
            SoundEvent whisper = PenumbraSounds.whisperVariant(host.getRandom());
            ServerPlayer owner = host.getOwnerPlayer();
            if (owner != null && owner.distanceTo(host) <= CHASE_WARNING_HOST_RANGE) {
                PenumbraNetworking.sendSound(owner, whisper, pos, 0.8f, pitch);
            }
        }
        host.discard();
    }

    /**
     * Replace l'Hôte {@code minDist}–{@code maxDist} blocs derrière le joueur
     * (même validation d'emplacement que VanishWhenSeenGoal.teleportBehind).
     *
     * @return false si aucun emplacement valide (l'Hôte reste alors sur place).
     */
    private static boolean teleportBehindPlayer(ServerPlayer player, HostEntity host,
                                                double minDist, double maxDist) {
        if (!(host.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        RandomSource random = host.getRandom();
        Vec3 look = player.getLookAngle().normalize();
        double dist = minDist + random.nextDouble() * Math.max(1.0, maxDist - minDist);
        int baseX = Mth.floor(player.getX() - look.x * dist);
        int baseZ = Mth.floor(player.getZ() - look.z * dist);
        int baseY = Mth.floor(player.getY());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 4; dy >= -12; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!isValidTeleportSpot(serverLevel, cursor)) {
                continue;
            }
            host.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5, random.nextFloat() * 360.0f, 0.0f);
            return true;
        }
        return false;
    }

    /** Emplacement valide : chunk chargé, sol solide, 2 blocs d'air, aucun liquide. */
    private static boolean isValidTeleportSpot(ServerLevel level, BlockPos feet) {
        if (!level.isLoaded(feet)) {
            return false;
        }
        BlockPos ground = feet.below();
        BlockPos head = feet.above();
        // 26.1 : isSolidRender() sans arguments (vérifié dans les sources 26.1.2).
        return level.getBlockState(ground).isSolidRender()
                && level.getBlockState(feet).isAir()
                && level.getBlockState(head).isAir()
                && level.getFluidState(ground).isEmpty()
                && level.getFluidState(feet).isEmpty()
                && level.getFluidState(head).isEmpty();
    }

    // ================= Phase 2 : réactions jumelles (SPEC2 §4) =================

    /**
     * Acquisition naturelle d'un Hôte réellement centré dans les jumelles. Un même
     * Hôte ne produit qu'un jet par session de zoom ; dézoomer réarme l'observation,
     * tandis que le cooldown global des réactions empêche le spam par bascule rapide.
     */
    private static void tickNaturalBinocularObservation(ServerPlayer player, long gameTime) {
        UUID playerId = player.getUUID();
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.binocularsEnabled || !config.binocularsHostReactionsEnabled
                || !PenumbraC2S.isZooming(playerId) || isInFalseCalm(player)
                || isAutomaticEventPaused(player) || isInGlobalCooldown(player)
                || !effectiveStageAtLeast(player,
                        currentState(playerId, profile(player).tension(), gameTime), 2)) {
            LAST_BINO_OBSERVED_HOST.remove(playerId);
            return;
        }
        HostEntity observed = findHostInBinocularGaze(player);
        if (observed == null) {
            return;
        }
        UUID previous = LAST_BINO_OBSERVED_HOST.put(playerId, observed.getUUID());
        if (!observed.getUUID().equals(previous)) {
            tryBinocularReaction(player, observed);
        }
    }

    /** Hôte visible, appartenant au joueur et le plus proche du centre du regard. */
    private static HostEntity findHostInBinocularGaze(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB box = player.getBoundingBox().inflate(BINO_OBSERVATION_RANGE);
        HostEntity best = null;
        double bestDot = BINO_OBSERVATION_DOT;
        for (HostEntity host : level.getEntities(EntityTypeTest.forClass(HostEntity.class), box,
                candidate -> candidate.isAlive() && !candidate.isInvisible()
                        && candidate.isOwnedBy(player)
                        && candidate.getBehavior() != HostBehavior.CHASING
                        && candidate.getBehavior() != HostBehavior.ATTACKING)) {
            Vec3 toHost = host.getEyePosition().subtract(eye);
            double distance = toHost.length();
            if (distance < 1.0 || distance > BINO_OBSERVATION_RANGE || !player.hasLineOfSight(host)) {
                continue;
            }
            double dot = look.dot(toHost.scale(1.0 / distance));
            if (dot > bestDot) {
                bestDot = dot;
                best = host;
            }
        }
        return best;
    }

    /**
     * Tente une réaction de l'Hôte observé aux jumelles : tirage de rareté
     * pondéré par l'état, puis choix pondéré parmi les 7 réactions en
     * excluant celles récentes ({@code wasRecent("bino_<type>")}).
     * Cooldown global de {@value #BINO_REACTION_COOLDOWN_TICKS} ticks par joueur ;
     * respect de {@code binocularsHostReactionsEnabled}.
     */
    public static void tryBinocularReaction(ServerPlayer player, HostEntity host) {
        PenumbraConfig config = PenumbraConfig.get();
        if (host == null || !host.isAlive() || !host.isOwnedBy(player)
                || !config.binocularsHostReactionsEnabled || isInFalseCalm(player)
                || isAutomaticEventPaused(player) || isInGlobalCooldown(player)) {
            return;
        }
        UUID playerId = player.getUUID();
        long gameTime = player.level().getGameTime();
        Long last = LAST_BINO_REACTION.get(playerId);
        if (last != null && gameTime - last < BINO_REACTION_COOLDOWN_TICKS) {
            return;
        }
        // Le cooldown porte sur le jet naturel, pas seulement sur un succès : basculer
        // les jumelles ne permet donc pas de forcer rapidement la probabilité.
        LAST_BINO_REACTION.put(playerId, gameTime);
        PlayerHorrorProfile profile = profile(player);
        TensionState state = currentState(playerId, profile.tension(), gameTime);
        if (player.getRandom().nextFloat() >= binocularRarity(state)) {
            return; // rareté : la plupart des observations ne produisent rien
        }
        String type = pickBinocularReaction(player, profile, state);
        if (type == null) {
            return;
        }
        if (applyBinocularReaction(player, host, type, gameTime)) {
            profile.markIncidental("bino_" + type);
            debugLog("Réaction jumelles '{}' pour {}", type, player.getGameProfile().name());
        }
    }

    /**
     * Surcharge à type imposé (commande /penumbra binoculars reaction, SPEC2 §8) :
     * bypass la rareté, le cooldown et la config (forçage administrateur,
     * cohérent avec {@link #forceEvent}).
     *
     * @return false si le type est inconnu ou la réaction impossible —
     *         permet à la commande d'afficher {@code reaction_unknown}.
     */
    public static boolean tryBinocularReaction(ServerPlayer player, HostEntity host, String forcedType) {
        if (host == null || !host.isAlive() || !BINO_REACTION_TYPES.contains(forcedType)
                || (host.hasOwner() && !host.isOwnedBy(player))) {
            return false;
        }
        if (!host.hasOwner()) {
            host.setOwner(player);
        }
        long gameTime = player.level().getGameTime();
        PlayerHorrorProfile profile = profile(player);
        if (!applyBinocularReaction(player, host, forcedType, gameTime)) {
            return false;
        }
        LAST_BINO_REACTION.put(player.getUUID(), gameTime);
        profile.markIncidental("bino_" + forcedType);
        debugLog("Réaction jumelles forcée '{}' pour {}", forcedType, player.getGameProfile().name());
        return true;
    }

    /** Chance qu'un appel non forcé produise une réaction, selon l'état de tension. */
    private static float binocularRarity(TensionState state) {
        return switch (state) {
            case CALM -> 0.15f;
            case UNEASE -> 0.25f;
            case WATCHED -> 0.35f;
            case MANIFESTATION -> 0.45f;
            case HUNT -> 0.55f;
            case CRISIS -> 0.65f;
            case FALSE_CALM -> 0.50f;
        };
    }

    /**
     * Choix pondéré d'une réaction : poids par état ({@link #BINO_WEIGHTS}),
     * exclusion des réactions récentes ({@code wasRecent("bino_<type>")}) et de
     * {@code reappear_checked} sans mémoire de lieux. Null si tout est exclu.
     */
    private static String pickBinocularReaction(ServerPlayer player, PlayerHorrorProfile profile, TensionState state) {
        int totalWeight = 0;
        int[] weights = new int[BINO_REACTION_TYPES.size()];
        for (int i = 0; i < weights.length; i++) {
            String type = BINO_REACTION_TYPES.get(i);
            int weight = profile.wasRecent("bino_" + type) ? 0 : BINO_WEIGHTS.get(type)[state.ordinal()];
            if (weight > 0 && type.equals("reappear_checked") && profile.homePoints().isEmpty()
                    && profile.randomFrequentCell(player.getRandom()).isEmpty()) {
                weight = 0; // aucune zone connue où réapparaître
            }
            weights[i] = weight;
            totalWeight += weight;
        }
        if (totalWeight <= 0) {
            return null;
        }
        int roll = player.getRandom().nextInt(totalWeight);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return BINO_REACTION_TYPES.get(i);
            }
        }
        return BINO_REACTION_TYPES.get(BINO_REACTION_TYPES.size() - 1);
    }

    /** Applique une réaction jumelle. Retourne false si elle n'a pu se produire. */
    private static boolean applyBinocularReaction(ServerPlayer player, HostEntity host, String type, long gameTime) {
        return switch (type) {
            case "freeze" -> doFreezeReaction(host);
            case "head_turn" -> doHeadTurnReaction(player, host);
            case "vanish_on_unzoom", "approach_between" -> armUnzoomReaction(player, host, type, gameTime);
            case "sound_behind" -> doSoundBehindReaction(player, host);
            case "mimic_posture" -> doMimicPostureReaction(player, host);
            case "reappear_checked" -> doReappearCheckedReaction(player, host);
            default -> false;
        };
    }

    /**
     * freeze : l'Hôte se fige (behavior DORMANT — ObservePlayerGoal stoppe sa
     * navigation) pendant {@value #BINO_FREEZE_TICKS} ticks, puis retrouve son
     * comportement antérieur s'il n'a pas changé entre-temps.
     */
    private static boolean doFreezeReaction(HostEntity host) {
        HostBehavior previous = host.getBehavior();
        host.getNavigation().stop();
        host.setBehavior(HostBehavior.DORMANT);
        if (host.level() instanceof ServerLevel serverLevel) {
            UUID hostId = host.getUUID();
            runLater(serverLevel, BINO_FREEZE_TICKS, () -> {
                // 26.1 : Level.getEntity(UUID) (re-fetch : l'Hôte peut avoir disparu).
                Entity entity = serverLevel.getEntity(hostId);
                if (entity instanceof HostEntity frozen && frozen.isAlive()
                        && frozen.getBehavior() == HostBehavior.DORMANT) {
                    frozen.setBehavior(previous == HostBehavior.DORMANT ? HostBehavior.OBSERVING : previous);
                }
            });
        }
        return true;
    }

    /**
     * head_turn : l'Hôte pivote lentement vers le joueur (LookControl immédiat,
     * behavior OBSERVING pour prolonger si ≤ 32 blocs) + murmure grave.
     * SPEC2 §4 : HOST_HEAD_CREAK n'existe pas au registre (SPEC2 §2) → repli
     * documenté sur HOST_WHISPER.
     */
    private static boolean doHeadTurnReaction(ServerPlayer player, HostEntity host) {
        host.getLookControl().setLookAt(player, 30.0f, 30.0f);
        if (host.getBehavior() == HostBehavior.DORMANT || host.getBehavior() == HostBehavior.HIDING) {
            host.setBehavior(HostBehavior.OBSERVING);
        }
        PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER, host.position(), 0.5f, 0.7f);
        return true;
    }

    /**
     * Arme une réaction différée au dézoom (vanish_on_unzoom / approach_between) :
     * déclenchée par le poll serveur dès que {@code PenumbraC2S.isZooming}
     * repasse à faux, ou expirée après {@value #BINO_ARMED_EXPIRY_TICKS} ticks.
     */
    private static boolean armUnzoomReaction(ServerPlayer player, HostEntity host, String type, long gameTime) {
        ARMED_BINO_REACTIONS.put(player.getUUID(),
                new ArmedBinoReaction(type, host.getUUID(), gameTime + BINO_ARMED_EXPIRY_TICKS));
        return true;
    }

    /**
     * sound_behind : l'Hôte se dissout sans un bruit à sa position, puis un
     * souffle (BREATH_BEHIND ou HOST_WHISPER) retentit 3 blocs derrière le joueur.
     */
    private static boolean doSoundBehindReaction(ServerPlayer player, HostEntity host) {
        if (host.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(PenumbraParticles.ASH_MOTE,
                    host.getX(), host.getY() + 1.3, host.getZ(), 24, 0.4, 0.8, 0.4, 0.01);
        }
        host.discard();
        Vec3 behind = player.position().subtract(player.getLookAngle().normalize().scale(3.0));
        // 0.5.0 : tirage parmi souffle et variantes de murmure (improvedSoundsEnabled).
        SoundEvent sound = player.getRandom().nextBoolean()
                ? PenumbraSounds.BREATH_BEHIND : PenumbraSounds.whisperVariant(player.getRandom());
        PenumbraNetworking.sendSound(player, sound, behind, 0.6f, 0.85f);
        return true;
    }

    /**
     * mimic_posture : l'Hôte copie sneak/sprint du joueur pendant
     * {@value #BINO_MIMIC_TICKS} ticks (pas de {@value #BINO_MIMIC_INTERVAL_TICKS}).
     */
    private static boolean doMimicPostureReaction(ServerPlayer player, HostEntity host) {
        if (!(host.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        mimicPostureStep(serverLevel, player.getUUID(), host.getUUID(), 0);
        return true;
    }

    /** Un pas de copie de posture ; se replanifie jusqu'à la fin de la réaction. */
    private static void mimicPostureStep(ServerLevel level, UUID playerId, UUID hostId, int step) {
        MinecraftServer server = level.getServer();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        Entity entity = level.getEntity(hostId);
        if (player == null || !(entity instanceof HostEntity host) || !host.isAlive()
                || !host.isOwnedBy(player)) {
            return;
        }
        host.setShiftKeyDown(player.isShiftKeyDown());
        host.setSprinting(player.isSprinting());
        if (step + 1 < BINO_MIMIC_TICKS / BINO_MIMIC_INTERVAL_TICKS) {
            runLater(level, BINO_MIMIC_INTERVAL_TICKS, () -> mimicPostureStep(level, playerId, hostId, step + 1));
        }
    }

    /**
     * reappear_checked : téléporte l'Hôte à 20–30 blocs du joueur, vers une
     * zone que le joueur fréquente (cellule fréquente pondérée, sinon refuge),
     * avec un murmure étouffé. Repli sur head_turn si aucun emplacement valide.
     */
    private static boolean doReappearCheckedReaction(ServerPlayer player, HostEntity host) {
        PlayerHorrorProfile profile = profile(player);
        RandomSource random = player.getRandom();
        Optional<BlockPos> zone = profile.randomFrequentCell(random);
        if (zone.isEmpty() && !profile.homePoints().isEmpty()) {
            List<BlockPos> homes = profile.homePoints();
            zone = Optional.of(homes.get(random.nextInt(homes.size())));
        }
        if (zone.isEmpty() || !(host.level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        Vec3 from = player.position();
        Vec3 to = Vec3.atCenterOf(zone.get());
        Vec3 dir = new Vec3(to.x - from.x, 0.0, to.z - from.z);
        if (dir.lengthSqr() < 1.0E-4) {
            dir = new Vec3(1.0, 0.0, 0.0);
        }
        dir = dir.normalize();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = (random.nextDouble() - 0.5) * (Math.PI / 3.0); // ±30° autour de la direction
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            double dx = dir.x * cos - dir.z * sin;
            double dz = dir.x * sin + dir.z * cos;
            double dist = 20.0 + random.nextDouble() * 10.0; // jamais au contact : 20–30 blocs
            int baseX = Mth.floor(from.x + dx * dist);
            int baseZ = Mth.floor(from.z + dz * dist);
            int baseY = Mth.floor(from.y);
            for (int dy = 4; dy >= -12; dy--) {
                cursor.set(baseX, baseY + dy, baseZ);
                if (!isValidTeleportSpot(serverLevel, cursor)) {
                    continue;
                }
                host.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5, random.nextFloat() * 360.0f, 0.0f);
                PenumbraNetworking.sendSound(player, PenumbraSounds.HOST_WHISPER,
                        new Vec3(baseX + 0.5, cursor.getY() + 1.0, baseZ + 0.5), 0.3f, 0.6f);
                return true;
            }
        }
        return doHeadTurnReaction(player, host); // repli garanti
    }

    /**
     * Poll serveur (toutes les 5 ticks) des réactions armées en attente de
     * dézoom : expiration après {@value #BINO_ARMED_EXPIRY_TICKS} ticks,
     * déclenchement dès que le joueur n'est plus en zoom.
     */
    private static void tickArmedBinocularReaction(ServerPlayer player, long gameTime) {
        UUID playerId = player.getUUID();
        ArmedBinoReaction armed = ARMED_BINO_REACTIONS.get(playerId);
        if (armed == null) {
            return;
        }
        if (gameTime > armed.expiryTick()) {
            ARMED_BINO_REACTIONS.remove(playerId);
            return;
        }
        if (PenumbraC2S.isZooming(playerId)) {
            return; // le joueur observe encore
        }
        ARMED_BINO_REACTIONS.remove(playerId); // consommée : max 1 fois par observation
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Entity entity = serverLevel.getEntity(armed.hostId());
        if (!(entity instanceof HostEntity host) || !host.isAlive() || !host.isOwnedBy(player)) {
            return;
        }
        if (armed.type().equals("vanish_on_unzoom")) {
            vanishInAsh(host);
        } else {
            approachBetweenOnUnzoom(player, host);
        }
    }

    /**
     * approach_between : au dézoom, l'Hôte s'est rapproché de 6–10 blocs le
     * long de la ligne Hôte→joueur, jamais à moins de
     * {@value #BINO_APPROACH_MIN_PLAYER_DIST} blocs du joueur. Passage en
     * STALKING si l'Hôte était passif. Sans effet en CHASING/ATTACKING.
     */
    private static void approachBetweenOnUnzoom(ServerPlayer player, HostEntity host) {
        if (host.getBehavior() == HostBehavior.CHASING || host.getBehavior() == HostBehavior.ATTACKING) {
            return;
        }
        double maxAdvance = host.distanceTo(player) - BINO_APPROACH_MIN_PLAYER_DIST;
        if (maxAdvance <= 0.0) {
            return; // déjà trop près : jamais de téléport au contact
        }
        double advance = Math.min(6.0 + player.getRandom().nextDouble() * 4.0, maxAdvance);
        Vec3 dir = player.position().subtract(host.position());
        Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
        if (flat.lengthSqr() < 1.0E-4 || !(host.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        flat = flat.normalize();
        int baseX = Mth.floor(host.getX() + flat.x * advance);
        int baseZ = Mth.floor(host.getZ() + flat.z * advance);
        int baseY = Mth.floor(host.getY());
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy >= -6; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!isValidTeleportSpot(serverLevel, cursor)) {
                continue;
            }
            host.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5, host.getYRot(), 0.0f);
            HostBehavior behavior = host.getBehavior();
            if (behavior == HostBehavior.OBSERVING || behavior == HostBehavior.DORMANT
                    || behavior == HostBehavior.HIDING) {
                host.setBehavior(HostBehavior.STALKING);
            }
            return;
        }
    }

    // ================= Phase 2 : jumpscares retardés (SPEC2 §4/§6) =================

    /** Un coffre vient d'être ouvert (UseBlockCallback, Agent_Events2) : arme chest_scare. */
    public static void noteChestOpened(ServerPlayer player, BlockPos pos) {
        armScare(player, "chest_scare", pos);
    }

    /**
     * Le joueur vient de passer une nuit au lit (réveil au matin) : comptabilise
     * l'utilisation du lit puis arme refuge_scare (le lit est au refuge).
     * Phase 3 : comptabilise aussi la découverte narrative {@code bed_used}
     * (règle 2→3, ×2 requis — le compteur est géré par StoryManager).
     */
    public static void noteSlept(ServerPlayer player) {
        profile(player).noteBedUse();
        StoryManager.noteDiscovery(player, StoryManager.KEY_BED_USED);
        armScare(player, "refuge_scare", null);
        // 0.6.0 : moments du réveil (coffre retrié, one-shot bed_shift).
        fr.penumbra.event.personal.PersonalMoments.onWake(player);
    }

    /** Une torche vient d'être soufflée (appelé par TorchSputterEvent) : arme torch_scare. */
    public static void noteTorchExtinguished(ServerPlayer player, BlockPos pos) {
        armScare(player, "torch_scare", pos);
    }

    /** 5 regards arrière en moins de 600 ticks (détecté par le directeur) : arme lookback_scare. */
    public static void noteLookedBack(ServerPlayer player) {
        armScare(player, "lookback_scare", null);
    }

    /** Aucun événement depuis 6000 ticks en état ≥ UNEASE : mémorise puis arme quiet_scare. */
    public static void noteLongQuiet(ServerPlayer player) {
        profile(player).noteQuietEvent(player.level().getGameTime());
        armScare(player, "quiet_scare", null);
    }

    /** Retour à moins de 10 blocs d'un refuge après plus de 1200 ticks d'absence : arme refuge_scare. */
    public static void noteRefugeReturn(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        PlayerHorrorProfile profile = profile(player);
        BlockPos nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos home : profile.homePoints()) {
            double distance = home.distSqr(player.blockPosition());
            if (distance < bestDistance) {
                nearest = home;
                bestDistance = distance;
            }
        }
        if (config.refugeContaminationEnabled && nearest != null) {
            RefugeContaminationModel.Audience audience = config.multiplayerContent
                    && hasNearbyActivePlayer(player)
                    ? RefugeContaminationModel.Audience.LOCAL_SHARED
                    : RefugeContaminationModel.Audience.OWNER_ONLY;
            RefugeContaminationManager.TriggerResult result =
                    RefugeContaminationManager.tryContaminate(player, nearest, audience);
            if (result.succeeded()) {
                StoryManager.noteDiscovery(player, StoryManager.KEY_REFUGE_CONTAMINATION);
                profile.markIncidental("refuge_" + result.effect().name().toLowerCase(java.util.Locale.ROOT));
                if (result.newStage() == RefugeContaminationModel.Stage.IMPOSSIBLE
                        && result.previousStage() != RefugeContaminationModel.Stage.IMPOSSIBLE
                        && player.level() instanceof ServerLevel level) {
                    UUID owner = player.getUUID();
                    BlockPos anchor = nearest.immutable();
                    runLater(level, 30, () -> {
                        ServerPlayer target = level.getServer().getPlayerList().getPlayer(owner);
                        if (target != null && target.isAlive()
                                && target.level() == level
                                && !ConfrontationManager.isActive(target.getUUID())
                                && target.blockPosition().distSqr(anchor) <= 24.0 * 24.0) {
                            PenumbraNetworking.sendJumpscare(target, "refuge_screamer", 0.92f);
                        }
                    });
                }
                return;
            }
        }
        armScare(player, "refuge_scare", null);
    }

    /**
     * Déclenche immédiatement un jumpscare retardé par son id (SPEC2 §6),
     * pour /penumbra scare : bypass le délai, les cooldowns, l'anti-doublon et
     * {@code delayedJumpscaresEnabled} (forçage administrateur, cohérent avec
     * {@link #forceEvent}).
     *
     * @return false si l'id est inconnu.
     */
    public static boolean forceScare(ServerPlayer player, String scareId) {
        if (!SCARE_IDS.contains(scareId)) {
            return false;
        }
        executeScare(player, scareId, null);
        long gameTime = player.level().getGameTime();
        PlayerHorrorProfile profile = profile(player);
        profile.markIncidental(scareId);
        SCARE_TYPE_LAST.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(scareId, gameTime);
        LAST_SCARE_ANY.put(player.getUUID(), gameTime);
        debugLog("Scare forcé '{}' pour {}", scareId, player.getGameProfile().name());
        return true;
    }

    /**
     * Arme un jumpscare retardé : délai aléatoire 100–400 ticks, cooldown de
     * {@value #SCARE_TYPE_COOLDOWN_TICKS} ticks par type (vérifié à l'armement,
     * consommé au déclenchement), anti-doublon de
     * {@value #SCARE_ANY_MIN_GAP_TICKS} ticks, un seul déclencheur armé par
     * type. Respect de {@code delayedJumpscaresEnabled}.
     *
     * @param pos position optionnelle du son (coffre, torche) ; position du
     *            joueur au moment du tir sinon.
     */
    private static void armScare(ServerPlayer player, String scareId, BlockPos pos) {
        long gameTime = player.level().getGameTime();
        if (!PenumbraConfig.get().delayedJumpscaresEnabled || !automaticEscalationAvailable(player)
                || !effectiveStageAtLeast(player,
                        currentState(player.getUUID(), profile(player).tension(), gameTime), 2)) {
            return;
        }
        UUID playerId = player.getUUID();
        Map<String, Long> typeLast = SCARE_TYPE_LAST.get(playerId);
        if (typeLast != null) {
            Long last = typeLast.get(scareId);
            if (last != null && gameTime - last < SCARE_TYPE_COOLDOWN_TICKS) {
                return; // cooldown par type
            }
        }
        Long lastAny = LAST_SCARE_ANY.get(playerId);
        if (lastAny != null && gameTime - lastAny < SCARE_ANY_MIN_GAP_TICKS) {
            return; // anti-doublon
        }
        Map<String, Long> armed = ARMED_SCARES.computeIfAbsent(playerId, key -> new HashMap<>());
        if (armed.containsKey(scareId)) {
            return; // déjà armé
        }
        long armToken = nextScareArmToken++;
        armed.put(scareId, armToken);
        if (!(player.level() instanceof ServerLevel level)) {
            armed.remove(scareId, armToken);
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            armed.remove(scareId, armToken);
            return;
        }
        int delay = SCARE_MIN_DELAY_TICKS
                + player.getRandom().nextInt(SCARE_MAX_DELAY_TICKS - SCARE_MIN_DELAY_TICKS + 1);
        BlockPos soundPos = pos == null ? null : pos.immutable();
        runLater(level, delay,
                () -> fireScare(level, server, playerId, scareId, armToken, soundPos));
    }

    /**
     * Tir d'un scare retardé : retrait de l'armement, gardes finales (joueur
     * présent et vivant, pas de cooldown post-poursuite, anti-doublon 800t),
     * puis jet de probabilité pondéré par la tension du moment
     * (× jumpscareFrequency). En cas d'échec du jet, aucun cooldown n'est
     * consommé : le prochain déclencheur pourra retenter.
     */
    private static void fireScare(ServerLevel expectedLevel, MinecraftServer server,
                                  UUID playerId, String scareId, long armToken, BlockPos pos) {
        Map<String, Long> armed = ARMED_SCARES.get(playerId);
        Long activeToken = armed == null ? null : armed.get(scareId);
        if (activeToken == null || activeToken.longValue() != armToken) {
            return; // reset, déconnexion, désarmement ou réarmement plus récent
        }
        armed.remove(scareId);
        if (armed.isEmpty()) {
            ARMED_SCARES.remove(playerId, armed);
        }
        if (!PenumbraConfig.get().delayedJumpscaresEnabled) {
            return; // la configuration peut avoir changé pendant le délai
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null || !player.isAlive() || player.isSpectator()
                || player.level() != expectedLevel) {
            return;
        }
        long gameTime = player.level().getGameTime();
        if (!automaticEscalationAvailable(player)) {
            return; // revalider grâce, vulnérabilité, phase et respiration au tir réel
        }
        Long lastAny = LAST_SCARE_ANY.get(playerId);
        if (lastAny != null && gameTime - lastAny < SCARE_ANY_MIN_GAP_TICKS) {
            return; // anti-doublon (deux armements proches)
        }
        PlayerHorrorProfile profile = profile(player);
        float chance = (SCARE_BASE_CHANCE + SCARE_TENSION_SCALE * (profile.tension() / PlayerHorrorProfile.MAX_TENSION))
                * Math.max(0f, PenumbraConfig.get().jumpscareFrequency);
        if (player.getRandom().nextFloat() >= chance) {
            return; // non déclenché : cooldowns non consommés
        }
        executeScare(player, scareId, pos);
        SCARE_TYPE_LAST.computeIfAbsent(playerId, key -> new HashMap<>()).put(scareId, gameTime);
        LAST_SCARE_ANY.put(playerId, gameTime);
        profile.markIncidental(scareId);
        profile.addTension(SCARE_TENSION_GAIN * Math.max(0f, PenumbraConfig.get().horrorIntensity));
        profile.directorMemory(ensureDirectorMemory(profile, gameTime).recordIncidental(gameTime));
        debugLog("Scare retardé '{}' pour {} (tension {})",
                scareId, player.getGameProfile().name(), profile.tension());
    }

    /**
     * Effet d'un scare : jumpscare client existant ("sting", "apparition",
     * "fake_creeper" via JumpscareManager) + son positionné, au coffre/à la
     * torche si une position a été capturée, sur le joueur sinon.
     * Phase 4 : les sons accompagnant un jumpscare passent par
     * {@code sendSoundPriority} — hors budget audio ordinaire ; seuls leurs
     * doublons strictement identiques dans le même tick peuvent être fusionnés.
     */
    private static void executeScare(ServerPlayer player, String scareId, BlockPos pos) {
        Vec3 at = pos != null ? Vec3.atCenterOf(pos) : player.position();
        switch (scareId) {
            case "chest_scare" -> {
                PenumbraNetworking.sendJumpscare(player, "sting", 0.8f);
                PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.HOST_WHISPER, at, 0.9f, 0.7f);
            }
            case "refuge_scare" -> {
                // Le packet "apparition" joue déjà le murmure chez la cible et
                // le partage avec les voisins : ne pas le doubler ici.
                PenumbraNetworking.sendJumpscare(player, "apparition", 0.85f);
            }
            case "torch_scare" -> {
                PenumbraNetworking.sendJumpscare(player, "sting", 0.6f);
                PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.TORCH_SPUTTER, at, 1.0f, 0.8f);
            }
            case "lookback_scare" -> {
                PenumbraNetworking.sendJumpscare(player, "apparition", 0.7f);
                Vec3 behind = player.position().subtract(player.getLookAngle().normalize().scale(1.5));
                PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.BREATH_BEHIND, behind, 0.5f, 0.8f);
            }
            case "inventory_scare" -> {
                PenumbraNetworking.sendJumpscare(player, "sting", 0.5f);
                PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.HOST_WHISPER, at, 0.6f, 1.1f);
            }
            case "quiet_scare" -> {
                PenumbraNetworking.sendJumpscare(player, "apparition", 0.6f);
                PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.SILENCE_DRONE, at, 0.4f, 1.0f);
            }
            default -> {
                // id inconnu : ne peut survenir qu'en appel interne erroné
            }
        }
    }

    // ================= Phase 2 : déclencheurs internes (SPEC2 §6) =================

    /**
     * Détection des regards arrière par delta du compteur persisté : fenêtre
     * glissante de {@value #LOOKBACK_WINDOW_TICKS} ticks ; au
     * {@value #LOOKBACK_TRIGGER_COUNT}e regard, {@link #noteLookedBack} est
     * appelé et la fenêtre repart à zéro.
     */
    private static void tickLookBackTracking(ServerPlayer player, PlayerHorrorProfile profile, long gameTime) {
        UUID playerId = player.getUUID();
        int current = profile.lookBackCount();
        Integer previous = LAST_LOOKBACK_COUNTS.put(playerId, current);
        if (previous == null || current <= previous) {
            return;
        }
        Deque<Long> times = LOOKBACK_TIMES.computeIfAbsent(playerId, key -> new ArrayDeque<>());
        for (int i = previous; i < current; i++) {
            times.addLast(gameTime);
        }
        while (!times.isEmpty() && gameTime - times.peekFirst() > LOOKBACK_WINDOW_TICKS) {
            times.pollFirst();
        }
        if (times.size() >= LOOKBACK_TRIGGER_COUNT) {
            times.clear();
            noteLookedBack(player);
        }
    }

    /**
     * « Retour au refuge » : joueur à moins de {@value #REFUGE_RETURN_RADIUS}
     * blocs d'un refuge connu après plus de {@value #REFUGE_ABSENCE_TICKS}
     * ticks d'absence. Le premier contact observé initialise le suivi sans
     * déclencher.
     */
    private static void tickRefugeReturn(ServerPlayer player, PlayerHorrorProfile profile, long gameTime) {
        if (!profile.isNearRefuge(player.blockPosition(), REFUGE_RETURN_RADIUS)) {
            return;
        }
        UUID playerId = player.getUUID();
        Long lastNear = LAST_NEAR_REFUGE.get(playerId);
        if (lastNear != null && gameTime - lastNear > REFUGE_ABSENCE_TICKS) {
            noteRefugeReturn(player);
        }
        LAST_NEAR_REFUGE.put(playerId, gameTime);
    }

    /** Détection du réveil au matin (sommeil → éveil, dehors il fait jour) → noteSlept. */
    private static void tickSleepWake(ServerPlayer player) {
        boolean sleeping = player.isSleeping();
        Boolean wasSleeping = WAS_SLEEPING.put(player.getUUID(), sleeping);
        // 26.1 : isDarkOutside() (isDay() n'existe plus, vérifié dans les sources 26.1.2).
        if (wasSleeping != null && wasSleeping && !sleeping && !player.level().isDarkOutside()) {
            noteSlept(player);
        }
    }

    /**
     * Ouverture de l'inventaire (signalée par le packet C2S d'Agent_Binoculars) :
     * chance pondérée par la tension d'armer inventory_scare.
     */
    private static void tickInventoryScare(ServerPlayer player, PlayerHorrorProfile profile) {
        UUID playerId = player.getUUID();
        boolean open = PenumbraC2S.isInventoryOpen(playerId);
        Boolean wasOpen = WAS_INVENTORY_OPEN.put(playerId, open);
        if (wasOpen == null || wasOpen || !open) {
            return;
        }
        float chance = INVENTORY_SCARE_BASE_CHANCE
                + INVENTORY_SCARE_TENSION_SCALE * (profile.tension() / PlayerHorrorProfile.MAX_TENSION);
        if (player.getRandom().nextFloat() < chance) {
            armScare(player, "inventory_scare", null);
        }
    }

    /** The persistent copy calendar is sampled at the five-second selection cadence. */
    private static void tickCopyCheck(ServerPlayer player) {
        if (isInFalseCalm(player) || isAutomaticEventPaused(player)) {
            return;
        }
        CopyManager.maybeBuildCopy(player);
    }

    /**
     * « Long silence » : aucun événement depuis {@value #LONG_QUIET_TICKS}
     * ticks en état ≥ UNEASE → {@link #noteLongQuiet}. Le tick est persisté
     * pour espacer les répétitions de la même durée.
     */
    private static void tickLongQuiet(ServerPlayer player, PlayerHorrorProfile profile, long gameTime) {
        if (isInFalseCalm(player) || isAutomaticEventPaused(player)) {
            return;
        }
        if (profile.tension() < TensionState.UNEASE.threshold) {
            return;
        }
        if (gameTime - profile.lastEventTick() <= LONG_QUIET_TICKS) {
            return;
        }
        if (gameTime - profile.lastQuietEventTick() <= LONG_QUIET_TICKS) {
            return;
        }
        noteLongQuiet(player);
    }

    // ================= Sélection d'événements (Phase 1, + filtre anti-frustration) =================

    /**
     * Sélection pondérée d'un événement : candidats filtrés par {@code canRun},
     * cooldown individuel et anti-répétition (deque de 6) ; poids
     * {@code weight(état) × eventFrequency × modulateur d'état} ; intervalle
     * minimal configurable, divisé par {@code eventFrequency}, depuis le
     * dernier événement. En grotte, le pool souterrain est favorisé et une
     * manifestation physique éligible devient prioritaire après une trop
     * longue série sans apparition.
     * Phase 2 : pendant le cooldown post-poursuite, les événements agressifs
     * (sightings) sont exclus (anti-frustration SPEC2 §4).
     */
    private static void trySelectEvent(ServerPlayer player, PlayerHorrorProfile profile, long gameTime) {
        PenumbraConfig config = PenumbraConfig.get();
        if (config.horrorIntensity <= 0f || config.eventFrequency <= 0f) {
            return; // horreur neutralisée par la configuration
        }
        if (isAutomaticEventPaused(player)) {
            return; // finale post-rituel : aucune sélection, quel que soit le type
        }
        UUID playerId = player.getUUID();
        // 0.5.0 : grâce persistée dans le profil (un redémarrage ne la réarme plus).
        long graceStart = profile.graceStartOrInit(gameTime);
        long graceTicks = gracePeriodTicks(config.eventGracePeriodSeconds);
        // Un nouveau profil commence sa dette au début de la grâce, pas à une
        // valeur nulle interprétée comme une urgence instantanée.
        LAST_MANIFESTATION_TICK.putIfAbsent(playerId, graceStart);
        if (!GRACE_BYPASSED.contains(playerId) && gameTime - graceStart < graceTicks) {
            return; // période de grâce
        }
        boolean underground = AbstractUndergroundEvent.isUnderground(player);
        DirectorMemory directorMemory = ensureDirectorMemory(profile, gameTime);
        HorrorArcEngine.Decision arcDecision;
        if (config.adaptiveHorrorArcsEnabled) {
            HorrorArcEngine.Context context = new HorrorArcEngine.Context(
                    underground,
                    profile.isNearRefuge(player.blockPosition(), 14.0),
                    PenumbraC2S.isZooming(playerId));
            arcDecision = HorrorArcEngine.tick(
                    directorMemory, gameTime, profile.tension(), context, arcSeed(player));
            directorMemory = arcDecision.memory();
            profile.directorMemory(directorMemory);
        } else {
            arcDecision = new HorrorArcEngine.Decision(
                    directorMemory, true, false, false, false, false);
        }
        TensionState state = currentState(playerId, profile.tension(), gameTime);
        if (state == TensionState.FALSE_CALM) {
            return; // vrai silence : aucun événement du pool pendant toute la fenêtre
        }
        if (arcDecision.falseCalm()) {
            // Une vraie respiration : la pression retombe progressivement vers
            // une zone encore inquiétante, sans effacer toute la progression.
            if (profile.tension() > 65.0f) {
                profile.addTension(-1.25f);
            }
            return;
        }
        if (FALSE_CALM_BREAK_PENDING.contains(playerId)) {
            if (tryRunFalseCalmBreak(player, profile, state, gameTime)) {
                FALSE_CALM_BREAK_PENDING.remove(playerId);
            }
            return; // la rupture unique précède toute reprise du pool normal
        }
        if (directorMemory.pacing().falseCalmBreakPending()
                && directorMemory.arc().phase() == DirectorMemory.ArcPhase.DORMANT) {
            if (tryRunFalseCalmBreak(player, profile, state, gameTime)) {
                profile.directorMemory(HorrorArcEngine.consumeBreak(
                        profile.directorMemory(), gameTime));
            }
            return;
        }

        long manifestationGapTicks = (long) config.manifestationMaxGapSeconds * 20L;
        long persistentManifestation = directorMemory.pacing().lastManifestationTick();
        boolean manifestationDue = pressurePhaseAllows(player)
                && (arcDecision.hardManifestationDue()
                || manifestationDue(persistentManifestation < 0L ? null : persistentManifestation,
                gameTime, manifestationGapTicks));
        boolean immediateSelection = IMMEDIATE_SELECTION_PENDING.contains(playerId);
        boolean arcBeatDue = arcDecision.beatDue()
                || directorMemory.arc().phase() == DirectorMemory.ArcPhase.DORMANT;
        if (!immediateSelection && !manifestationDue && !arcBeatDue) {
            return;
        }
        long minGap = minimumEventGapTicks(
                config.eventMinGapSeconds, config.eventFrequency, state);
        if (config.adaptiveHorrorArcsEnabled) {
            minGap = Math.max(SELECTION_INTERVAL_TICKS, (long) Math.ceil(minGap
                    * HorrorArcEngine.gapMultiplier(directorMemory.arc().phase())));
        }
        if (!immediateSelection && !DirectorPacingRules.selectionGapElapsed(
                directorMemory.pacing(), gameTime, minGap)) {
            return;
        }
        boolean hostCapOk = canSpawnHorrorEntityNear(player, DIRECTOR_SPAWN_GUARD_RADIUS);

        boolean succeeded;
        if (manifestationDue) {
            // Dette dure : uniquement des manifestations physiques. Les ids
            // récents sont permis, les cooldowns et garde-fous de placement restent.
            CandidatePool physical = buildCandidatePool(player, profile, state, gameTime,
                    underground, hostCapOk,
                    true, true, true, false);
            succeeded = runCandidatePool(player, profile, state, gameTime,
                    physical.events(), physical.weights());
            if (!succeeded) {
                // Dernier recours visible, sans bloc ni entité : jamais un bruit.
                succeeded = tryRunReliableVisualFallback(
                        player, profile, state, gameTime);
            }
        } else {
            int audioStreak = directorMemory.pacing().audioStreak();
            boolean skipAudio = audioStreak >= Math.max(1, config.maxConsecutiveAudioEvents);
            CandidatePool candidates = buildCandidatePool(player, profile, state, gameTime,
                    underground, hostCapOk,
                    false, false, false, skipAudio);
            if (candidates.isEmpty()) {
                // Deuxième passe : un historique plein ne peut plus bloquer le
                // directeur. L'id récent est fortement pénalisé, jamais gratuit.
                candidates = buildCandidatePool(player, profile, state, gameTime,
                        underground, hostCapOk,
                        false, true, false, skipAudio);
            }
            succeeded = runCandidatePool(player, profile, state, gameTime,
                    candidates.events(), candidates.weights());
        }
        if (succeeded) {
            IMMEDIATE_SELECTION_PENDING.remove(playerId);
        }
    }

    /** Pool immuable local à une sélection. */
    private record CandidatePool(List<HorrorEvent> events, List<Integer> weights) {
        private boolean isEmpty() {
            return events.isEmpty();
        }
    }

    /**
     * Construit un pool sans facteur commun de fréquence : la fréquence pilote
     * le tempo, tandis que les poids ne conservent que leurs rapports utiles.
     */
    private static CandidatePool buildCandidatePool(
            ServerPlayer player, PlayerHorrorProfile profile, TensionState state,
            long gameTime, boolean underground,
            boolean hostCapOk, boolean manifestationOnly, boolean allowRecent,
            boolean excludeReliableVisual, boolean skipAudio) {
        List<HorrorEvent> events = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        Map<String, Long> lastRuns = profile.directorMemory().pacing().eventCooldowns();
        boolean pressureAvailable = automaticSceneAvailable(player);
        for (HorrorEvent event : HorrorEvents.ALL) {
            String id = event.id();
            boolean preferredManifestation = isPreferredManifestation(id, underground);
            if (manifestationOnly && (!preferredManifestation
                    || excludeReliableVisual && RELIABLE_VISUAL_EVENT_IDS.contains(id))) {
                continue;
            }
            if (skipAudio && AUDIO_EVENT_IDS.contains(id)) {
                continue;
            }
            if (!isEventUnlocked(player, id, state)
                    || isPressureEvent(id) && !pressureAvailable) {
                continue;
            }
            boolean recent = profile.wasRecent(id);
            if (recent && !allowRecent) {
                continue;
            }
            if (lastRuns != null) {
                Long lastRun = lastRuns.get(id);
                if (lastRun != null && gameTime - lastRun < event.cooldownTicks()) {
                    continue;
                }
            }
            if (!event.canRun(player, profile)
                    || consumesHostSlot(id) && !hostCapOk) {
                continue;
            }
            int baseWeight = event.weight(state);
            if (baseWeight <= 0) {
                continue;
            }
            int weight = Math.max(1, Math.round(baseWeight
                    / (1f + 0.15f * profile.seenCount(id))));
            String family = eventFamily(id);
            long recentFamilyCount = profile.directorMemory().pacing().recentFamilies().stream()
                    .filter(family::equals).count();
            weight = Math.max(1, Math.round(weight
                    * HorrorArcEngine.themeMultiplier(profile.directorMemory().arc().theme(), family)
                    / (1f + 0.35f * recentFamilyCount)));
            if (underground && UNDERGROUND_EVENT_IDS.contains(id)) {
                weight = Math.max(1, Math.round(weight * 1.75f));
            }
            if (recent) {
                weight = Math.max(1, weight / 4);
            }
            events.add(event);
            weights.add(weight);
        }
        return new CandidatePool(events, weights);
    }

    /**
     * Repli de dette : choisit le visuel contextuel le moins vu et ignore son
     * cooldown, mais jamais les options, le stade, le contexte ou le verrou de groupe.
     */
    private static boolean tryRunReliableVisualFallback(
            ServerPlayer player, PlayerHorrorProfile profile, TensionState state,
            long gameTime) {
        if (!automaticSceneAvailable(player) || !PenumbraConfig.get().contextualScreamersEnabled) {
            return false;
        }
        List<HorrorEvent> fallbacks = new ArrayList<>();
        for (String id : RELIABLE_VISUAL_EVENT_IDS) {
            HorrorEvent event = HorrorEvents.byId(id);
            if (event != null && isEventUnlocked(player, id, state)
                    && event.weight(state) > 0 && event.canRun(player, profile)) {
                fallbacks.add(event);
            }
        }
        fallbacks.sort((left, right) -> Integer.compare(
                profile.seenCount(left.id()), profile.seenCount(right.id())));
        for (HorrorEvent fallback : fallbacks) {
            if (runAutomaticEvent(player, profile, fallback, state, gameTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Essaie au plus trois candidats pondérés distincts. Un événement dont le
     * placement est devenu invalide ne gaspille donc plus tout le cycle de
     * sélection ; seuls les succès consomment le gap et les cooldowns.
     */
    private static boolean runCandidatePool(ServerPlayer player, PlayerHorrorProfile profile,
                                            TensionState state, long gameTime,
                                            List<HorrorEvent> candidates, List<Integer> weights) {
        int attempts = Math.min(3, candidates.size());
        for (int attempt = 0; attempt < attempts && !candidates.isEmpty(); attempt++) {
            int totalWeight = 0;
            for (int weight : weights) {
                totalWeight += Math.max(0, weight);
            }
            if (totalWeight <= 0) {
                return false;
            }
            int roll = player.getRandom().nextInt(totalWeight);
            int chosenIndex = weights.size() - 1;
            for (int i = 0; i < weights.size(); i++) {
                roll -= Math.max(0, weights.get(i));
                if (roll < 0) {
                    chosenIndex = i;
                    break;
                }
            }
            HorrorEvent chosen = candidates.remove(chosenIndex);
            weights.remove(chosenIndex);
            if (runAutomaticEvent(player, profile, chosen, state, gameTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Joue une seule rupture sobre à la sortie de FALSE_CALM. Les candidats sont
     * essayés dans un ordre aléatoire ; si le contexte les invalide tous (sommeil,
     * changement de niveau), la rupture reste en attente pour la prochaine sélection.
     */
    private static boolean tryRunFalseCalmBreak(ServerPlayer player, PlayerHorrorProfile profile,
                                                TensionState state, long gameTime) {
        int start = player.getRandom().nextInt(FALSE_CALM_BREAK_EVENTS.size());
        for (int i = 0; i < FALSE_CALM_BREAK_EVENTS.size(); i++) {
            HorrorEvent event = HorrorEvents.byId(
                    FALSE_CALM_BREAK_EVENTS.get((start + i) % FALSE_CALM_BREAK_EVENTS.size()));
            if (event == null || !event.canRun(player, profile)) {
                continue;
            }
            if (runAutomaticEvent(player, profile, event, state, gameTime)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Exécute un événement et applique toutes ses conséquences : historique
     * anti-répétition, cooldown individuel, gain de tension (+6 à +14 selon
     * l'état, +20 pour un jumpscare majeur, le tout × horrorIntensity), puis
     * programmation du faux calme si l'événement survient en CRISIS.
     */
    private static boolean runEvent(ServerPlayer player, PlayerHorrorProfile profile, HorrorEvent event,
                                    TensionState state, long gameTime) {
        PenumbraConfig config = PenumbraConfig.get();
        final boolean succeeded;
        try {
            succeeded = event.run(player);
        } catch (Exception exception) {
            // Un emplacement devenu invalide ou un contenu tiers défaillant ne
            // doit jamais faire tomber toute la boucle serveur.
            PenumbraMod.LOGGER.error("[Penumbra] Événement '{}' en échec pour {}",
                    event.id(), player.getGameProfile().name(), exception);
            return false;
        }
        if (!succeeded) {
            debugLog("Événement '{}' annulé sans effet pour {}", event.id(), player.getGameProfile().name());
            return false;
        }
        noteStoryDiscoveries(player, event);
        boolean manifestation = countsAsManifestation(event.id());
        profile.markEvent(gameTime, event.id());
        profile.addTension(tensionGain(state, event) * Math.max(0f, config.horrorIntensity));
        DirectorMemory memory = ensureDirectorMemory(profile, gameTime)
                .recordDirected(gameTime, event.id(), eventFamily(event.id()),
                        AUDIO_EVENT_IDS.contains(event.id()), manifestation);
        memory = HorrorArcEngine.afterSuccessfulBeat(
                memory, gameTime, manifestation, arcSeed(player));
        profile.directorMemory(memory);
        LAST_EVENT_RUNS.computeIfAbsent(player.getUUID(), key -> new HashMap<>()).put(event.id(), gameTime);
        if (AUDIO_EVENT_IDS.contains(event.id())) {
            CONSECUTIVE_AUDIO_EVENTS.merge(player.getUUID(), 1,
                    (left, right) -> Math.min(16, left + right));
        } else {
            CONSECUTIVE_AUDIO_EVENTS.remove(player.getUUID());
        }
        if (manifestation) {
            noteManifestation(player, state, gameTime);
        }
        if (config.debugLogging) {
            PenumbraMod.LOGGER.info("[Penumbra] Événement '{}' pour {} (état {}, tension {})",
                    event.id(), player.getGameProfile().name(), state, profile.tension());
        }
        if (!manifestation && MAJOR_JUMPSCARE_EVENTS.contains(event.id())) {
            noteCrisisManifestation(player, state);
        } else if (state != TensionState.CRISIS) {
            CRISIS_MANIFESTATION_COUNTS.remove(player.getUUID());
        }
        return true;
    }

    /**
     * Variante réservée aux sélections automatiques. Les scènes lourdes sont
     * espacées pour leur cible et coordonnées lorsqu'un autre joueur est proche.
     * Les commandes opérateur utilisent directement runEvent.
     */
    private static boolean runAutomaticEvent(ServerPlayer player,
                                             PlayerHorrorProfile profile,
                                             HorrorEvent event,
                                             TensionState state,
                                             long gameTime) {
        boolean coordinated = usesGroupCoordination(event.id());
        if (isPressureEvent(event.id()) && !automaticSceneAvailable(player)
                || coordinated && !localGroupSceneAvailable(player)) {
            debugLog("Scène '{}' différée pour {} : respiration ou groupe déjà occupé",
                    event.id(), player.getGameProfile().name());
            return false;
        }
        boolean succeeded = runEvent(player, profile, event, state, gameTime);
        if (succeeded && coordinated) {
            recordAutomaticScene(player);
        }
        return succeeded;
    }

    private static boolean hasNearbyActivePlayer(ServerPlayer player) {
        double radiusSqr = MultiplayerEventPacing.LOCAL_SCENE_RADIUS
                * MultiplayerEventPacing.LOCAL_SCENE_RADIUS;
        for (ServerPlayer other : player.level().players()) {
            if (other != player && other.isAlive() && !other.isSpectator()
                    && other.distanceToSqr(player) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    /**
     * Réservation en lecture pour les managers autonomes (Rampant, Absent,
     * Originel, Contrefait, Cavaleuse). Une commande forcée ne doit pas appeler
     * cette API. La respiration personnelle s'applique aussi en solo.
     */
    public static boolean automaticSceneAvailable(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator() || !isGraceComplete(player)
                || ConfrontationManager.isActive(player.getUUID())
                || PenumbraConfig.get().horrorIntensity <= 0f
                || PenumbraConfig.get().eventFrequency <= 0f
                || isInFalseCalm(player) || isAutomaticEventPaused(player)
                || isInGlobalCooldown(player) || isRecovering(player)
                || hasActiveChase(player) || !pressurePhaseAllows(player)) {
            return false;
        }
        long now = player.level().getGameTime();
        if (!DirectorPacingRules.sceneGapElapsed(
                ensureDirectorMemory(profile(player), now).pacing(), now)) {
            return false;
        }
        return localGroupSceneAvailable(player);
    }

    private static boolean localGroupSceneAvailable(ServerPlayer player) {
        if (!hasNearbyActivePlayer(player)) {
            return true;
        }
        MinecraftServer server = player.level().getServer();
        MultiplayerEventPacing pacing = MULTIPLAYER_PACING.computeIfAbsent(
                server, ignored -> new MultiplayerEventPacing());
        Vec3 position = player.position();
        return pacing.isAvailable(
                player.level().dimension().identifier().toString(),
                position.x, position.y, position.z,
                server.overworld().getGameTime());
    }

    /** Enregistre une scène réelle dans le rythme personnel, puis dans le groupe proche. */
    public static void recordAutomaticScene(ServerPlayer player) {
        long now = player.level().getGameTime();
        PlayerHorrorProfile profile = profile(player);
        profile.directorMemory(ensureDirectorMemory(profile, now).recordIncidental(now));
        if (!hasNearbyActivePlayer(player)) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        MultiplayerEventPacing pacing = MULTIPLAYER_PACING.computeIfAbsent(
                server, ignored -> new MultiplayerEventPacing());
        Vec3 position = player.position();
        pacing.record(player.level().dimension().identifier().toString(),
                position.x, position.y, position.z,
                server.overworld().getGameTime());
    }

    private static boolean pressurePhaseAllows(ServerPlayer player) {
        return !PenumbraConfig.get().adaptiveHorrorArcsEnabled
                || DirectorPacingRules.allowsPressure(
                ensureDirectorMemory(profile(player), player.level().getGameTime()).arc().phase());
    }

    private static boolean automaticEscalationAvailable(ServerPlayer player) {
        return !player.isSleeping() && automaticSceneAvailable(player)
                && (!PenumbraConfig.get().adaptiveHorrorArcsEnabled
                || DirectorPacingRules.allowsEscalation(
                profile(player).directorMemory().arc().phase()));
    }

    private static boolean isVulnerable(ServerPlayer player) {
        return DirectorPacingRules.vulnerable(player.getHealth(), player.getMaxHealth(),
                player.getFoodData().getFoodLevel(), player.getAirSupply(),
                player.isOnFire(), player.hurtTime > 0);
    }

    private static boolean isRecovering(ServerPlayer player) {
        if (isVulnerable(player)) {
            return true;
        }
        Long until = VULNERABILITY_COOLDOWN_UNTIL.get(player.getUUID());
        if (until != null && player.level().getGameTime() < until) {
            return true;
        }
        VULNERABILITY_COOLDOWN_UNTIL.remove(player.getUUID());
        return false;
    }

    private static boolean hasActiveChase(ServerPlayer player) {
        UUID activeId = ACTIVE_CHASES.get(player.getUUID());
        if (activeId != null && player.level() instanceof ServerLevel level) {
            Entity active = level.getEntityInAnyDimension(activeId);
            if (active instanceof HostEntity host && host.isAlive()) {
                return true;
            }
            ACTIVE_CHASES.remove(player.getUUID());
        }
        return false;
    }

    /** Ordre tournant partagé par les managers autonomes, stable dans un cycle. */
    public static List<ServerPlayer> fairAutomaticPlayers(
            MinecraftServer server, int sourceIntervalTicks, int sourceSalt) {
        long round = MultiplayerEventPacing.fairRound(
                server.getTickCount(), sourceIntervalTicks, sourceSalt);
        return MultiplayerEventPacing.fairOrder(
                server.getPlayerList().getPlayers(), round, ServerPlayer::getUUID);
    }

    /** Classification pure exposée aux tests de régression multijoueur. */
    static boolean usesGroupCoordination(String eventId) {
        return GROUP_COORDINATED_EVENT_IDS.contains(eventId);
    }

    /** Stade minimal d'un événement, exposé au package pour les tests de régression. */
    static int minimumStageForEvent(String eventId) {
        return MINIMUM_EVENT_STAGES.getOrDefault(eventId, 0);
    }

    /**
     * Stade temporairement débloqué par la tension. Une commande à 100 doit
     * ouvrir CRISIS et ses manifestations même si la sauvegarde narrative est
     * encore jeune ; la progression persistante, elle, n'est pas modifiée.
     */
    static int stageUnlockedByTension(TensionState state) {
        return switch (state) {
            case CALM, FALSE_CALM -> 0;
            case UNEASE -> 1;
            case WATCHED -> 2;
            case MANIFESTATION -> 3;
            case HUNT -> 4;
            case CRISIS -> 5;
        };
    }

    /** Le mode histoire désactivé ne masque jamais de contenu. */
    private static boolean effectiveStageAtLeast(ServerPlayer player, TensionState state, int minimum) {
        if (!PenumbraConfig.get().storyEnabled) {
            return true;
        }
        int effective = Math.max(StoryManager.getStage(player), stageUnlockedByTension(state));
        return effective >= minimum;
    }

    private static boolean isEventUnlocked(ServerPlayer player, String eventId, TensionState state) {
        return effectiveStageAtLeast(player, state, minimumStageForEvent(eventId));
    }

    /** Le calme persistant doit aussi calmer l'ambiance client et les consommateurs externes. */
    static TensionState perceivedState(UUID playerId, PlayerHorrorProfile profile, long gameTime) {
        if (profile.postRitualCalm().advanceTo(gameTime).active()
                || DirectorPacingRules.falseCalmActive(profile.directorMemory().arc(), gameTime)) {
            return TensionState.FALSE_CALM;
        }
        return currentState(playerId, profile.tension(), gameTime);
    }

    /**
     * Phase 3 : comptabilise les découvertes narratives liées à l'exécution
     * d'un événement d'horreur — toutes passent par ce point central
     * (sélection naturelle comme forçage admin) :
     * <ul>
     *   <li>{@code first_anomaly} : notée au premier événement du joueur
     *       (règle 0→1 ; le compteur reste à 1, les appels suivants sont
     *       évités — {@code noteDiscovery} gère déjà la persistance) ;</li>
     *   <li>{@code mimic_event} : événements d'imitation {@code footsteps},
     *       {@code fake_silhouette}, {@code lure_door} (règle 1→2, ×3) ;</li>
     *   <li>{@code host_observed} : événements d'observation de l'Hôte
     *       ({@link #HOST_OBSERVATION_EVENT_IDS} — règle 4→5, ×4).</li>
     * </ul>
     * O(1) : trois tests d'appartenance et une lecture de compteur par
     * événement, jamais d'allocation par tick.
     */
    private static void noteStoryDiscoveries(ServerPlayer player, HorrorEvent event) {
        String id = event.id();
        if (MIMIC_EVENT_IDS.contains(id)) {
            StoryManager.noteDiscovery(player, StoryManager.KEY_MIMIC_EVENT);
        }
        if (isHostObservationEvent(id)) {
            noteHostObserved(player);
        }
        if (StoryManager.getCounters(player).getOrDefault(StoryManager.KEY_FIRST_ANOMALY, 0) == 0) {
            StoryManager.noteDiscovery(player, StoryManager.KEY_FIRST_ANOMALY);
            // Phase 4 : badge « Première fissure » (grant idempotent côté avancements).
            PenumbraBadges.grant(player, "first_anomaly");
        }
    }

    /** État courant : FALSE_CALM tant qu'il est actif, sinon dérivé de la tension. */
    private static TensionState currentState(UUID playerId, float tension, long gameTime) {
        Long falseCalmUntil = FALSE_CALM_UNTIL.get(playerId);
        if (falseCalmUntil != null && gameTime < falseCalmUntil) {
            return TensionState.FALSE_CALM;
        }
        if (falseCalmUntil != null) {
            FALSE_CALM_UNTIL.remove(playerId); // la rupture reste suivie séparément
        }
        return TensionState.forTension(tension);
    }

    /** Gain de tension d'un événement réussi : +6..+14 selon l'état, +20 si jumpscare majeur. */
    private static float tensionGain(TensionState state, HorrorEvent event) {
        if (MAJOR_JUMPSCARE_EVENTS.contains(event.id())) {
            return MAJOR_JUMPSCARE_TENSION;
        }
        return switch (state) {
            case CALM -> 6f;
            case UNEASE -> 8f;
            case WATCHED -> 10f;
            case MANIFESTATION -> 12f;
            case HUNT, CRISIS, FALSE_CALM -> 14f;
        };
    }

    /** Modulateur d'agressivité des poids selon l'état (FALSE_CALM est filtré avant sélection). */
    private static float stateWeightModulator(TensionState state) {
        return switch (state) {
            case CALM -> 0.6f;
            case UNEASE -> 0.8f;
            case WATCHED -> 1.0f;
            case MANIFESTATION -> 1.2f;
            case HUNT -> 1.4f;
            case CRISIS -> 1.6f;
            case FALSE_CALM -> 0.0f;
        };
    }

    /** Modulateur de l'intervalle minimal entre événements selon l'état. */
    private static float minGapModulator(TensionState state) {
        return switch (state) {
            case CALM -> 1.5f;
            case UNEASE -> 1.2f;
            case WATCHED -> 1.0f;
            case MANIFESTATION -> 0.8f;
            case HUNT -> 0.6f;
            case CRISIS -> 0.5f;
            case FALSE_CALM -> 1.0f;
        };
    }

    /** Durée de grâce convertie en ticks, bornée contre les valeurs non nettoyées. */
    static long gracePeriodTicks(int seconds) {
        return (long) Mth.clamp(seconds, 0, 900) * 20L;
    }

    /** Intervalle effectif entre événements, avec un plancher égal à une sélection (5 s). */
    static long minimumEventGapTicks(int baseSeconds, float frequency, TensionState state) {
        if (!Float.isFinite(frequency) || frequency <= 0.0f) {
            return Long.MAX_VALUE;
        }
        double raw = Mth.clamp(baseSeconds, 5, 300) * 20.0
                / Mth.clamp(frequency, 0.01f, 4.0f)
                * minGapModulator(state);
        return Math.max(SELECTION_INTERVAL_TICKS, (long) Math.ceil(raw));
    }

    /** Vrai si aucune manifestation n'a encore réussi ou si sa dette a expiré. */
    static boolean manifestationDue(Long lastManifestationTick, long gameTime, long maxGapTicks) {
        if (lastManifestationTick == null || gameTime < lastManifestationTick) {
            return true;
        }
        return gameTime - lastManifestationTick >= Math.max(1L, maxGapTicks);
    }

    /** Classification pure du filtre de population d'Hôtes. */
    static boolean consumesHostSlot(String eventId) {
        return HOST_SLOT_EVENT_IDS.contains(eventId);
    }

    /** Classification pure des événements soldant immédiatement la dette. */
    static boolean countsAsManifestation(String eventId) {
        return MANIFESTATION_EVENT_IDS.contains(eventId);
    }

    /** Classification pure du filtre anti-frustration post-poursuite. */
    static boolean isAggressiveEvent(String eventId) {
        return AGGRESSIVE_EVENT_IDS.contains(eventId);
    }

    /** Inclut les apparitions et stings que l'ancien filtre agressif omettait. */
    static boolean isPressureEvent(String eventId) {
        return isAggressiveEvent(eventId) || countsAsManifestation(eventId)
                || MAJOR_JUMPSCARE_EVENTS.contains(eventId) || "fake_creeper".equals(eventId);
    }

    /** Classification pure des succès montrant réellement un Hôte. */
    static boolean isHostObservationEvent(String eventId) {
        return HOST_OBSERVATION_EVENT_IDS.contains(eventId);
    }

    /** En grotte, la garantie choisit un événement conçu pour cet espace. */
    private static boolean isPreferredManifestation(String eventId, boolean underground) {
        return underground
                ? UNDERGROUND_MANIFESTATION_EVENT_IDS.contains(eventId)
                : countsAsManifestation(eventId);
    }

    /** Famille stable utilisée par l'arc et l'anti-habituation transversale. */
    static String eventFamily(String eventId) {
        if (AUDIO_EVENT_IDS.contains(eventId)) {
            return "audio";
        }
        if (eventId.contains("refuge") || eventId.equals("door_anomaly")
                || eventId.equals("dawn_door") || eventId.equals("torch_chain")) {
            return "refuge";
        }
        if (UNDERGROUND_EVENT_IDS.contains(eventId)) {
            return "cave";
        }
        if (MIMIC_EVENT_IDS.contains(eventId) || eventId.equals("mimic_intrusion")) {
            return "mimic";
        }
        if (eventId.contains("binoculars") || eventId.contains("projection")) {
            return "photo";
        }
        if (countsAsManifestation(eventId)) {
            return "sighting";
        }
        if (isAggressiveEvent(eventId)) {
            return "chase";
        }
        return "environment";
    }

    // ================= Helpers internes =================

    /** Profil d'horreur d'un joueur (créé vierge si inconnu). */
    private static PlayerHorrorProfile profile(ServerPlayer player) {
        return PenumbraPersistentState.get(player.level().getServer()).profile(player.getUUID());
    }

    /** Initialise paresseusement la migration 0.11 -> 0.12 sans dette instantanée. */
    private static DirectorMemory ensureDirectorMemory(
            PlayerHorrorProfile profile, long gameTime) {
        DirectorMemory memory = profile.directorMemory();
        if (memory == null || memory.pacing().lastClockTick() <= 0L) {
            memory = DirectorMemory.fresh(gameTime, profile.lastEventTick());
        } else {
            memory = memory.rebaseClock(gameTime);
        }
        profile.directorMemory(memory);
        return memory;
    }

    /** Graine stable par joueur pour conserver les durées d'arc après reload. */
    private static long arcSeed(ServerPlayer player) {
        UUID id = player.getUUID();
        return id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 23);
    }

    /** Log de debug uniforme (silencieux si {@code debugLogging} est faux). */
    private static void debugLog(String message, Object... args) {
        if (PenumbraConfig.get().debugLogging) {
            PenumbraMod.LOGGER.info("[Penumbra] " + message, args);
        }
    }
}
