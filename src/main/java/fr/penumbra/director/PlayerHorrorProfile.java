package fr.penumbra.director;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Profil horrifique persistant d'un joueur : tension (0–100), historique
 * anti-répétition des 6 derniers événements, statistiques comportementales
 * (temps sous terre, immobilité, regards arrière) et — Phase 2 (SPEC2 §5) —
 * mémoire comportementale : refuges appris ({@code homePoints}), cellules
 * grossières fréquentées ({@code frequentCells}), nuits au lit
 * ({@code bedUses}) et tick du dernier événement « long silence »
 * ({@code lastQuietEventTick}).
 * <p>
 * Codec v2 : TOUS les champs sont OPTIONNELS avec défauts, donc une
 * sauvegarde Phase 1 (qui ne contient que les 6 premiers champs) se charge
 * sans erreur. Le compteur SPEC2 {@code lookBackCount} est le compteur
 * Phase 1 {@code looksBehind} : même donnée, clé de persistance d'origine
 * conservée pour compatibilité ascendante.
 * </p>
 */
public class PlayerHorrorProfile {

    /**
     * Cellule grossière (x&gt;&gt;3, z&gt;&gt;3) fréquentée par le joueur :
     * clé packée, dernier y observé et compteur de passages (borné).
     */
    private record CellCount(long cell, int y, int count) {
        /** Codec d'une cellule (sous-structure introduite en v2 : champs obligatoires). */
        static final Codec<CellCount> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("cell").forGetter(CellCount::cell),
                Codec.INT.fieldOf("y").forGetter(CellCount::y),
                Codec.INT.fieldOf("count").forGetter(CellCount::count)
        ).apply(instance, CellCount::new));
    }

    /**
     * Codec v2 complet du profil (persistance monde). Champs tous optionnels
     * avec défauts : toute valeur absente (monde Phase 1 ou champ corrompu)
     * retombe sur son défaut au lieu d'invalider le profil.
     */
    public static final Codec<PlayerHorrorProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("tension", 0f).forGetter(PlayerHorrorProfile::tension),
            Codec.LONG.optionalFieldOf("lastEventTick", 0L).forGetter(PlayerHorrorProfile::lastEventTick),
            Codec.STRING.listOf().optionalFieldOf("recentEvents", List.of())
                    .forGetter(profile -> List.copyOf(profile.recentEvents)),
            Codec.INT.optionalFieldOf("totalEvents", 0).forGetter(PlayerHorrorProfile::totalEvents),
            Codec.LONG.optionalFieldOf("ticksUnderground", 0L).forGetter(PlayerHorrorProfile::ticksUnderground),
            Codec.INT.optionalFieldOf("looksBehind", 0).forGetter(PlayerHorrorProfile::looksBehind),
            BlockPos.CODEC.listOf().optionalFieldOf("homePoints", List.of())
                    .forGetter(profile -> List.copyOf(profile.homePoints)),
            CellCount.CODEC.listOf().optionalFieldOf("frequentCells", List.of())
                    .forGetter(profile -> List.copyOf(profile.frequentCells.values())),
            Codec.INT.optionalFieldOf("bedUses", 0).forGetter(PlayerHorrorProfile::bedUses),
            Codec.LONG.optionalFieldOf("lastQuietEventTick", 0L).forGetter(PlayerHorrorProfile::lastQuietEventTick),
            // 0.5.0 : point de départ de la période de grâce, persisté (-1 = pas encore initialisé).
            // Avant, il vivait en mémoire : chaque redémarrage serveur ré-offrait 7,5 min
            // de grâce à tout le monde, ce qui diluait le pacing des habitués.
            Codec.LONG.optionalFieldOf("graceStartTick", -1L).forGetter(PlayerHorrorProfile::graceStartTick),
            // 0.6.0 : compteur de vécus par événement (anti-habituation), borné à 50 par id.
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("eventSeen", Map.of())
                    .forGetter(profile -> Map.copyOf(profile.eventSeenCounts)),
            // 0.12.0 : arcs, dette et cooldowns du directeur, optionnels pour les anciens mondes.
            DirectorMemory.CODEC.optionalFieldOf("directorV2")
                    .forGetter(profile -> Optional.of(profile.directorMemory))
    ).apply(instance, PlayerHorrorProfile::new));

    /** Tension maximale. */
    public static final float MAX_TENSION = 100f;
    /** Taille de la deque anti-répétition. */
    private static final int RECENT_EVENTS_MAX = 6;
    /** Hauteur sous laquelle le joueur est considéré sous terre. */
    private static final double UNDERGROUND_Y = 60.0;
    /** Luminosité maximale pour être « dans le noir ». */
    private static final int DARK_LIGHT_LEVEL = 4;
    /** Variation de yaw (degrés) qualifiant un regard arrière. */
    private static final float LOOK_BEHIND_DEGREES = 120f;
    /** Fenêtre maximale (ticks) du regard arrière. */
    private static final long LOOK_BEHIND_WINDOW_TICKS = 10L;

    // --- Constantes Phase 2 : mémoire comportementale (SPEC2 §5) ---
    /** Immobilité continue requise pour compter une « visite longue » de refuge. */
    private static final int REFUGE_STILL_TICKS = 200;
    /** Luminosité maximale admise pour apprendre un refuge (la nuit suffit toujours). */
    private static final int REFUGE_MAX_LIGHT = 8;
    /** Visites longues requises avant de promouvoir un emplacement en refuge. */
    private static final int REFUGE_VISITS_REQUIRED = 2;
    /** Nombre maximal de refuges mémorisés. */
    private static final int MAX_HOME_POINTS = 4;
    /** Distance² de regroupement d'une visite longue avec un candidat/refuge connu (10 blocs). */
    private static final double REFUGE_GROUP_DIST_SQR = 100.0;
    /** Nombre maximal de candidats refuges en cours d'apprentissage. */
    private static final int MAX_REFUGE_CANDIDATES = 8;
    /** Nombre maximal de cellules fréquentes mémorisées. */
    private static final int MAX_FREQUENT_CELLS = 16;
    /** Borne du compteur de passages d'une cellule (anti-dérive). */
    private static final int MAX_CELL_COUNT = 6000;
    /** Cadence d'échantillonnage des cellules fréquentées (ticks). */
    private static final int CELL_SAMPLE_INTERVAL = 20;

    private float tension;
    private long lastEventTick;
    private final Deque<String> recentEvents;
    private int totalEvents;
    private long ticksUnderground;
    private int looksBehind;

    // --- Phase 2 : champs persistés (SPEC2 §5) ---
    private final List<BlockPos> homePoints;
    private final Map<Long, CellCount> frequentCells;
    private int bedUses;
    private long lastQuietEventTick;
    /** 0.5.0 : tick de départ de la grâce (persisté ; -1 tant que non initialisé). */
    private long graceStartTick;
    /** 0.6.0 : vécus par événement (anti-habituation), persistés, bornés à 50. */
    private final Map<String, Integer> eventSeenCounts;
    /** 0.12.0 : pacing persistant, remplacé atomiquement par le moteur d'arcs. */
    private DirectorMemory directorMemory;

    /** 0.6.0 : borne du compteur de vécus par événement. */
    private static final int MAX_EVENT_SEEN = 50;
    private static final int MAX_EVENT_IDS = 128;
    /** 0.6.0 : cadence d'échantillonnage du ruban de trajet (1 s). */
    private static final int PATH_SAMPLE_INTERVAL = 20;
    /** 0.6.0 : taille du ruban de trajet (90 échantillons = 90 s). */
    private static final int PATH_MAX_SAMPLES = 90;

    /** 0.6.0 : ruban de trajet transitoire (échantillon/s, non persisté). */
    private final transient Deque<net.minecraft.world.phys.Vec3> pathRibbon = new ArrayDeque<>();
    private transient int pathSampleCounter;

    // --- Données transitoires de tracking (non persistées, hors Codec) ---
    private transient float lastYaw;
    private transient long lastYawTick = Long.MIN_VALUE;
    private transient double lastX;
    private transient double lastY;
    private transient double lastZ;
    private transient boolean positionInitialized;
    private transient int stillTicks;
    private transient int cellSampleCounter;
    private transient List<RefugeCandidate> refugeCandidates;
    private transient String trackingDimension = "";
    private transient boolean overworldMemoryActive = true;

    /** Candidat refuge en cours d'apprentissage (transitoire, non persisté). */
    private static final class RefugeCandidate {
        private final BlockPos pos;
        private int visits;

        private RefugeCandidate(BlockPos pos, int visits) {
            this.pos = pos;
            this.visits = visits;
        }
    }

    /** Profil vierge (nouveau joueur). */
    public PlayerHorrorProfile() {
        this(0f, 0L, List.of(), 0, 0L, 0, List.of(), List.of(), 0, 0L, -1L,
                Map.of(), Optional.empty());
    }

    /** Constructeur utilisé par le Codec (valeurs déjà défautées par optionalFieldOf). */
    private PlayerHorrorProfile(float tension, long lastEventTick, List<String> recentEvents,
                                int totalEvents, long ticksUnderground, int looksBehind,
                                List<BlockPos> homePoints, List<CellCount> frequentCells,
                                int bedUses, long lastQuietEventTick, long graceStartTick,
                                Map<String, Integer> eventSeenCounts,
                                Optional<DirectorMemory> directorMemory) {
        this.tension = Mth.clamp(tension, 0f, MAX_TENSION);
        this.lastEventTick = lastEventTick;
        this.recentEvents = new ArrayDeque<>();
        int firstRecent = Math.max(0, recentEvents.size() - RECENT_EVENTS_MAX);
        for (int i = firstRecent; i < recentEvents.size(); i++) {
            String id = recentEvents.get(i);
            if (id != null && !id.isBlank() && id.length() <= 64) {
                this.recentEvents.addLast(id);
            }
        }
        this.totalEvents = Math.max(0, totalEvents);
        this.ticksUnderground = Math.max(0L, ticksUnderground);
        this.looksBehind = Math.max(0, looksBehind);
        this.homePoints = new ArrayList<>();
        for (BlockPos pos : homePoints) {
            if (this.homePoints.size() >= MAX_HOME_POINTS) {
                break;
            }
            this.homePoints.add(pos.immutable());
        }
        this.frequentCells = new HashMap<>();
        for (CellCount cell : frequentCells) {
            if (this.frequentCells.size() >= MAX_FREQUENT_CELLS) {
                break;
            }
            this.frequentCells.put(cell.cell(), new CellCount(cell.cell(), cell.y(),
                    Mth.clamp(cell.count(), 1, MAX_CELL_COUNT)));
        }
        this.bedUses = Math.max(0, bedUses);
        this.lastQuietEventTick = lastQuietEventTick;
        this.graceStartTick = graceStartTick;
        this.eventSeenCounts = new HashMap<>();
        for (Map.Entry<String, Integer> entry : eventSeenCounts.entrySet()) {
            if (this.eventSeenCounts.size() >= MAX_EVENT_IDS) {
                break;
            }
            String id = entry.getKey();
            if (id != null && !id.isBlank() && id.length() <= 64) {
                this.eventSeenCounts.put(id, Mth.clamp(entry.getValue(), 0, MAX_EVENT_SEEN));
            }
        }
        this.directorMemory = directorMemory.orElseGet(
                () -> DirectorMemory.fresh(0L, this.lastEventTick));
    }

    /** Mémoire persistante du directeur adaptatif. */
    public DirectorMemory directorMemory() {
        return this.directorMemory;
    }

    /** Remplacement atomique de la mémoire immuable après une transition. */
    public void directorMemory(DirectorMemory value) {
        if (value != null) {
            this.directorMemory = value;
        }
    }

    /** 0.6.0 : nombre de fois où le joueur a déjà vécu cet événement (0 si jamais). */
    public int seenCount(String eventId) {
        return this.eventSeenCounts.getOrDefault(eventId, 0);
    }

    /**
     * 0.6.0 : copie du ruban de trajet (échantillons de position, 1/s, le plus
     * ancien en tête). Transitoire — vidé au redémarrage. Consommé par
     * l'événement {@code echoed_path}.
     */
    public List<net.minecraft.world.phys.Vec3> copyPath() {
        return List.copyOf(this.pathRibbon);
    }

    /** 0.5.0 : tick de départ de la grâce (-1 tant que non initialisé). */
    public long graceStartTick() {
        return this.graceStartTick;
    }

    /**
     * 0.5.0 : point de départ de la grâce, initialisé au premier appel et
     * persisté ensuite — un redémarrage serveur ne réarme plus la grâce.
     */
    public long graceStartOrInit(long gameTime) {
        if (this.graceStartTick < 0L) {
            this.graceStartTick = gameTime;
        }
        return this.graceStartTick;
    }

    /** 0.5.0 : réarme explicitement la grâce (reset administrateur). */
    public void restartGrace(long gameTime) {
        this.graceStartTick = gameTime;
    }

    /** Tension actuelle (0–100). Jamais affichée au joueur. */
    public float tension() {
        return this.tension;
    }

    /** Ajoute (ou retire si négatif) de la tension, bornée entre 0 et 100. */
    public void addTension(float delta) {
        this.tension = Mth.clamp(this.tension + delta, 0f, MAX_TENSION);
    }

    /**
     * Retire {@code perSecond} points de tension. Appelé par le directeur
     * une fois par seconde (toutes les 20 ticks) — le taux effectif est donc
     * exactement {@code perSecond} points par seconde.
     */
    public void decayTension(float perSecond) {
        addTension(-perSecond);
    }

    /** Tick de jeu du dernier événement déclenché (0 si aucun). */
    public long lastEventTick() {
        return this.lastEventTick;
    }

    /**
     * Enregistre un événement : met à jour la deque anti-répétition
     * (6 derniers ids, le plus récent en queue) et incrémente le compteur total.
     */
    public void markEvent(long tick, String eventId) {
        this.lastEventTick = tick;
        this.recentEvents.addLast(eventId);
        while (this.recentEvents.size() > RECENT_EVENTS_MAX) {
            this.recentEvents.pollFirst();
        }
        this.totalEvents++;
        // 0.6.0 : compteur de vécus par id (anti-habituation), borné à 50.
        this.eventSeenCounts.merge(eventId, 1, (a, b) -> Math.min(MAX_EVENT_SEEN, a + b));
    }

    /**
     * Enregistre un effet incident (jumelles, scare retardé) sans repousser le
     * prochain beat dirigé et sans polluer la deque anti-répétition du pool.
     */
    public void markIncidental(String eventId) {
        if (eventId == null || eventId.isBlank() || eventId.length() > 64) {
            return;
        }
        this.eventSeenCounts.merge(eventId, 1,
                (left, right) -> Math.min(MAX_EVENT_SEEN, left + right));
    }

    /** Vrai si l'événement figure parmi les 6 derniers déclenchés. */
    public boolean wasRecent(String eventId) {
        return this.recentEvents.contains(eventId);
    }

    /**
     * Tracking comportemental, appelé chaque tick serveur par le directeur :
     * temps sous terre (y &lt; 60 et lumière ≤ 4), immobilité, regards
     * arrière (variation de yaw &gt; 120° en moins de 10 ticks), puis —
     * Phase 2 — apprentissage des refuges et des cellules fréquentées
     * (compteurs bornés, aucun scan de blocs).
     */
    public void tickBehavior(ServerPlayer player) {
        long now = player.level().getGameTime();

        String dimension = player.level().dimension().identifier().toString();
        if (!dimension.equals(this.trackingDimension)) {
            this.trackingDimension = dimension;
            this.pathRibbon.clear();
            this.refugeCandidates = null;
            this.positionInitialized = false;
            this.lastYawTick = Long.MIN_VALUE;
            this.stillTicks = 0;
        }
        // Les anciennes sauvegardes ne stockaient pas la dimension des refuges.
        // Ils restent donc explicitement liés à l'Overworld plutôt que d'être
        // interprétés aux mêmes coordonnées dans le Nether ou l'End.
        this.overworldMemoryActive = player.level().dimension().equals(Level.OVERWORLD);

        // 0.6.0 : ruban de trajet — un échantillon de position par seconde,
        // fenêtre glissante de 90 s (transitoire, consommé par echoed_path).
        if (++this.pathSampleCounter >= PATH_SAMPLE_INTERVAL) {
            this.pathSampleCounter = 0;
            this.pathRibbon.addLast(player.position());
            while (this.pathRibbon.size() > PATH_MAX_SAMPLES) {
                this.pathRibbon.pollFirst();
            }
        }

        // Regards arrière : rotation brusque de la tête.
        float yaw = player.getYRot();
        if (this.lastYawTick != Long.MIN_VALUE) {
            long elapsed = now - this.lastYawTick;
            if (elapsed > 0 && elapsed < LOOK_BEHIND_WINDOW_TICKS
                    && Math.abs(Mth.wrapDegrees(yaw - this.lastYaw)) > LOOK_BEHIND_DEGREES) {
                this.looksBehind++;
            }
        }
        this.lastYaw = yaw;
        this.lastYawTick = now;

        // Sous terre : sous y=60 dans une zone sombre.
        if (player.getY() < UNDERGROUND_Y
                && player.level().getMaxLocalRawBrightness(player.blockPosition()) <= DARK_LIGHT_LEVEL) {
            this.ticksUnderground++;
        }

        // Immobilité : position strictement identique au tick précédent.
        if (this.positionInitialized
                && player.getX() == this.lastX && player.getY() == this.lastY && player.getZ() == this.lastZ) {
            this.stillTicks++;
        } else {
            this.stillTicks = 0;
        }
        this.lastX = player.getX();
        this.lastY = player.getY();
        this.lastZ = player.getZ();
        this.positionInitialized = true;

        // Phase 2 : mémoire comportementale (faible coût : compteurs uniquement).
        if (this.overworldMemoryActive) {
            trackRefugeLearning(player);
        }
        if (this.overworldMemoryActive && ++this.cellSampleCounter >= CELL_SAMPLE_INTERVAL) {
            this.cellSampleCounter = 0;
            trackFrequentCell(player);
        }
    }

    /**
     * Apprentissage des refuges : une « visite longue » est comptée au
     * franchissement unique de {@value #REFUGE_STILL_TICKS} ticks d'immobilité
     * (le compteur retombe à zéro dès que le joueur bouge), de nuit ou dans
     * une lumière &lt; {@value #REFUGE_MAX_LIGHT}. Deux visites longues au
     * même endroit (±10 blocs) promeuvent un refuge. Dix secondes et deux
     * visites suffisent : l'ancien seuil de trois immobilités de 30 s était opaque.
     */
    private void trackRefugeLearning(ServerPlayer player) {
        if (this.stillTicks != REFUGE_STILL_TICKS) {
            return;
        }
        boolean night = player.level().isDarkOutside();
        if (!night && player.level().getMaxLocalRawBrightness(player.blockPosition()) >= REFUGE_MAX_LIGHT) {
            return; // ni nuit ni pénombre : pas un refuge
        }
        BlockPos pos = player.blockPosition();
        if (this.refugeCandidates == null) {
            this.refugeCandidates = new ArrayList<>();
        }
        for (RefugeCandidate candidate : this.refugeCandidates) {
            if (candidate.pos.distSqr(pos) <= REFUGE_GROUP_DIST_SQR) {
                candidate.visits++;
                if (candidate.visits >= REFUGE_VISITS_REQUIRED) {
                    promoteHomePoint(candidate.pos);
                    this.refugeCandidates.remove(candidate);
                }
                return;
            }
        }
        if (this.refugeCandidates.size() < MAX_REFUGE_CANDIDATES) {
            this.refugeCandidates.add(new RefugeCandidate(pos.immutable(), 1));
        }
    }

    /** Promeut un emplacement en refuge (≤ {@value #MAX_HOME_POINTS}, éviction FIFO). */
    private void promoteHomePoint(BlockPos pos) {
        for (BlockPos existing : this.homePoints) {
            if (existing.distSqr(pos) <= REFUGE_GROUP_DIST_SQR) {
                return; // déjà connu
            }
        }
        if (this.homePoints.size() >= MAX_HOME_POINTS) {
            this.homePoints.remove(0);
        }
        this.homePoints.add(pos.immutable());
    }

    /**
     * Comptabilise la cellule grossière (x&gt;&gt;3, z&gt;&gt;3) courante :
     * compteur incrémenté (borné à {@value #MAX_CELL_COUNT}) ; au-delà de
     * {@value #MAX_FREQUENT_CELLS} entrées, la cellule la moins fréquentée
     * est évincée.
     */
    private void trackFrequentCell(ServerPlayer player) {
        int cellX = Mth.floor(player.getX()) >> 3;
        int cellZ = Mth.floor(player.getZ()) >> 3;
        int y = Mth.floor(player.getY());
        long key = packCell(cellX, cellZ);
        CellCount existing = this.frequentCells.get(key);
        if (existing != null) {
            this.frequentCells.put(key, new CellCount(key, y, Math.min(existing.count() + 1, MAX_CELL_COUNT)));
            return;
        }
        if (this.frequentCells.size() >= MAX_FREQUENT_CELLS) {
            long weakestKey = key;
            int weakestCount = Integer.MAX_VALUE;
            for (Map.Entry<Long, CellCount> entry : this.frequentCells.entrySet()) {
                if (entry.getValue().count() < weakestCount) {
                    weakestCount = entry.getValue().count();
                    weakestKey = entry.getKey();
                }
            }
            this.frequentCells.remove(weakestKey);
        }
        this.frequentCells.put(key, new CellCount(key, y, 1));
    }

    /** Packe les coordonnées d'une cellule (x&gt;&gt;3, z&gt;&gt;3) dans un long. */
    private static long packCell(int cellX, int cellZ) {
        return ((long) cellX << 32) | (cellZ & 0xFFFFFFFFL);
    }

    /** Nombre de regards arrière détectés. */
    public int looksBehind() {
        return this.looksBehind;
    }

    /** Ticks passés sous terre dans le noir. */
    public long ticksUnderground() {
        return this.ticksUnderground;
    }

    /** Nombre total d'événements subis (helper additif pour le Codec et /penumbra status). */
    public int totalEvents() {
        return this.totalEvents;
    }

    /** Id du dernier événement déclenché, ou null si aucun (helper additif pour /penumbra status). */
    public String lastEventId() {
        return this.recentEvents.peekLast();
    }

    /** Ticks consécutifs d'immobilité, transitoire (helper additif pour les canRun d'événements). */
    public int stillTicks() {
        return this.stillTicks;
    }

    // ================= Phase 2 : mémoire comportementale (SPEC2 §5) =================

    /** Refuges appris (≤ {@value #MAX_HOME_POINTS}, positions immuables). Jamais affichés au joueur. */
    public List<BlockPos> homePoints() {
        return this.overworldMemoryActive ? List.copyOf(this.homePoints) : List.of();
    }

    /** Vrai si {@code pos} est à moins de {@code radius} blocs d'un refuge connu. */
    public boolean isNearRefuge(BlockPos pos, double radius) {
        if (!this.overworldMemoryActive) {
            return false;
        }
        double radiusSqr = radius * radius;
        for (BlockPos home : this.homePoints) {
            if (home.distSqr(pos) <= radiusSqr) {
                return true;
            }
        }
        return false;
    }

    /**
     * Choisit une cellule fréquentée, pondérée par son compteur de passages.
     * Retourne le centre de la cellule (y = dernier y observé lors d'un
     * passage), ou {@link Optional#empty()} si aucune cellule n'est mémorisée.
     */
    public Optional<BlockPos> randomFrequentCell(RandomSource random) {
        if (!this.overworldMemoryActive || this.frequentCells.isEmpty()) {
            return Optional.empty();
        }
        int total = 0;
        for (CellCount cell : this.frequentCells.values()) {
            total += cell.count();
        }
        int roll = random.nextInt(Math.max(1, total));
        CellCount chosen = null;
        for (CellCount cell : this.frequentCells.values()) {
            roll -= cell.count();
            if (roll < 0) {
                chosen = cell;
                break;
            }
        }
        if (chosen == null) {
            chosen = this.frequentCells.values().iterator().next();
        }
        int cellX = (int) (chosen.cell() >> 32);
        int cellZ = (int) chosen.cell();
        return Optional.of(new BlockPos((cellX << 3) + 4, chosen.y(), (cellZ << 3) + 4));
    }

    /** Comptabilise une nuit passée dans un lit (appelé par HorrorDirector.noteSlept). */
    public void noteBedUse() {
        this.bedUses++;
    }

    /** Nombre de nuits passées dans un lit. */
    public int bedUses() {
        return this.bedUses;
    }

    /**
     * Nombre total de regards arrière détectés. Alias SPEC2 §5 de
     * {@link #looksBehind()} : même donnée, clé de persistance Phase 1
     * ({@code "looksBehind"}) conservée pour compatibilité des sauvegardes.
     */
    public int lookBackCount() {
        return this.looksBehind;
    }

    /** Tick du dernier événement « long silence » déclenché par le directeur (0 si jamais). */
    public long lastQuietEventTick() {
        return this.lastQuietEventTick;
    }

    /** Enregistre le déclenchement d'un événement « long silence » (SPEC2 §6). */
    public void noteQuietEvent(long tick) {
        this.lastQuietEventTick = tick;
    }
}
