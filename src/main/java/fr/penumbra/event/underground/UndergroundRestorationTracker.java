package fr.penumbra.event.underground;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracker de restauration des événements souterrains (Phase 3).
 * <p>
 * Petit {@link SavedData} persistant (pattern {@code PenumbraPersistentState}) mémorisant
 * chaque bloc modifié par un événement souterrain : position, dimension, état d'origine,
 * état posé, type d'événement et échéance de restauration automatique ({@link #NO_DEADLINE}
 * = permanent, retirable uniquement via {@link #restoreAll(ServerLevel)}).
 * Objectif global de {@value #MAX_ENTRIES} entrées. Une éviction FIFO devient une
 * restauration immédiatement exigible ; si sa dimension ou son chunk est indisponible,
 * l'entrée reste persistée au-delà de cette limite et sera réessayée plus tard.
 * </p>
 * <p>
 * Règle d'or : une restauration n'écrase JAMAIS une modification du joueur — le bloc n'est
 * restauré que si l'état actuel est encore exactement celui que l'événement avait posé
 * (comparaison d'instances canoniques, identité).
 * L'horloge des échéances est le temps de jeu de l'overworld ({@link #gameTime(ServerLevel)}),
 * identique pour toutes les dimensions.
 * </p>
 * <p>
 * API publique pour l'intégrateur : {@link #restoreAll(ServerLevel)} (à brancher sur
 * {@code /penumbra debug restoration}) et {@link #onServerTick(MinecraftServer)} (throttlé
 * 1/{@value #TICK_THROTTLE} ticks, à enregistrer sur {@code ServerTickEvents.END_SERVER_TICK}).
 * La vérification des échéances est aussi appelée, amortie, au début des run() des événements.
 * </p>
 */
public final class UndergroundRestorationTracker extends SavedData {

    /** Type d'entrée : minerai illusoire ({@code FalseVeinEvent}). */
    public static final String TYPE_VEIN = "false_vein";
    /** Type d'entrée : torche étrangère permanente ({@code ForeignTorchEvent}). */
    public static final String TYPE_TORCH = "foreign_torch";
    /** Type d'entrée : galerie décalée ({@code ShiftedGalleryEvent}). */
    public static final String TYPE_GALLERY = "shifted_gallery";
    /** Type d'entrée : salle impossible ({@code ImpossibleRoomEvent}). */
    public static final String TYPE_ROOM = "impossible_room";
    /** Type générique : lumière ou bloc d'ambiance temporaire, persistant au redémarrage. */
    public static final String TYPE_TEMPORARY_EFFECT = "temporary_effect";

    /** Échéance signifiant « permanent » : jamais de restauration automatique. */
    public static final long NO_DEADLINE = -1L;

    /** Objectif d'entrées mémorisées (les restaurations en attente peuvent le dépasser). */
    private static final int MAX_ENTRIES = 256;
    /** Période réelle du tick de maintenance : 1 exécution tous les 200 appels. */
    private static final int TICK_THROTTLE = 200;

    /**
     * Entrée de restauration : bloc modifié par un événement.
     *
     * @param pos       position du bloc
     * @param dimension dimension du bloc
     * @param oldState  état d'origine (à restaurer)
     * @param newState  état posé par l'événement (restauration seulement si encore présent)
     * @param type      type d'événement ({@link #TYPE_VEIN}, {@link #TYPE_TORCH}, …)
     * @param deadline  échéance en temps de jeu overworld, ou {@link #NO_DEADLINE}
     * @param player    UUID du joueur concerné (chaîne, vide si non pertinent)
     */
    public record Entry(BlockPos pos, ResourceKey<Level> dimension, BlockState oldState,
                        BlockState newState, String type, long deadline, String player) {

        /** Codec d'une entrée (champs obligatoires sauf {@code player}, défaut chaîne vide). */
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Level.RESOURCE_KEY_CODEC.fieldOf("dim").forGetter(Entry::dimension),
                BlockState.CODEC.fieldOf("old").forGetter(Entry::oldState),
                BlockState.CODEC.fieldOf("neu").forGetter(Entry::newState),
                Codec.STRING.fieldOf("type").forGetter(Entry::type),
                Codec.LONG.fieldOf("deadline").forGetter(Entry::deadline),
                Codec.STRING.optionalFieldOf("player", "").forGetter(Entry::player)
        ).apply(instance, Entry::new));
    }

    /** Codec complet de l'état : simple liste d'entrées. */
    public static final Codec<UndergroundRestorationTracker> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                    Entry.CODEC.listOf().optionalFieldOf("entries", List.of())
                            .forGetter(tracker -> List.copyOf(tracker.entries))
            ).apply(instance, UndergroundRestorationTracker::new));

    // Persistance 26.1 (même pattern que PenumbraPersistentState) : SavedDataType record,
    // DataFixTypes null pour un mod, (dé)sérialisation entièrement pilotée par le codec.
    private static final SavedDataType<UndergroundRestorationTracker> TYPE =
            new SavedDataType<>(PenumbraMod.id("underground_restoration"),
                    UndergroundRestorationTracker::new, CODEC, null);

    private final List<Entry> entries;
    /** Compteur d'appels de {@link #onServerTick(MinecraftServer)} (throttle). */
    private static int tickCounter;

    /** État vierge (nouveau monde). */
    public UndergroundRestorationTracker() {
        this(List.of());
    }

    /** Constructeur utilisé par le Codec. */
    private UndergroundRestorationTracker(List<Entry> entries) {
        this.entries = new ArrayList<>(entries);
    }

    /** Récupère l'état persistant du monde (overworld), en le créant vierge si nécessaire. */
    public static UndergroundRestorationTracker get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Horloge commune des échéances : temps de jeu de l'overworld. */
    public static long gameTime(ServerLevel level) {
        return level.getServer().overworld().getGameTime();
    }

    /**
     * Mémorise un bloc modifié. Au-delà de {@value #MAX_ENTRIES} entrées, les plus
     * anciennes sont rendues immédiatement exigibles. Elles ne sont retirées de l'état
     * persistant qu'une fois leur chunk accessible et la restauration résolue.
     */
    public void add(ServerLevel level, Entry entry) {
        this.entries.add(entry);
        deferOverflowRestorations(level.getServer());
        setDirty();
    }

    /** Nombre d'entrées d'un type donné pour un joueur donné (ex. cap des torches). */
    public int countFor(String playerUuid, String type) {
        int count = 0;
        for (Entry entry : this.entries) {
            if (entry.type().equals(type) && entry.player().equals(playerUuid)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Évince la plus ancienne entrée d'un type pour un joueur (FIFO ciblé). Retourne
     * vrai seulement si l'entrée a pu être résolue et retirée. Si sa dimension ou son
     * chunk est indisponible, elle est conservée, rendue exigible et sera réessayée.
     */
    public boolean evictOldestFor(String playerUuid, String type, ServerLevel level) {
        for (int i = 0; i < this.entries.size(); i++) {
            Entry entry = this.entries.get(i);
            if (entry.type().equals(type) && entry.player().equals(playerUuid)) {
                if (restoreIfAvailable(level.getServer(), entry)) {
                    this.entries.remove(i);
                    setDirty();
                    return true;
                }
                Entry deferred = dueNow(entry, gameTime(level));
                if (deferred != entry) {
                    this.entries.set(i, deferred);
                    setDirty();
                }
                return false;
            }
        }
        return false;
    }

    /**
     * Vérifie les échéances arrivées à terme pour la dimension de {@code level} et
     * restaure les blocs encore intacts. Peu coûteux (≤ {@value #MAX_ENTRIES} entrées,
     * aucun scan de monde) : appelé amorti au début des run() des événements.
     */
    public static void checkDeadlines(ServerLevel level) {
        UndergroundRestorationTracker tracker = get(level.getServer());
        if (tracker.entries.isEmpty()) {
            return;
        }
        long now = gameTime(level);
        boolean changed = false;
        for (int i = tracker.entries.size() - 1; i >= 0; i--) {
            Entry entry = tracker.entries.get(i);
            if (entry.deadline() >= 0L && now >= entry.deadline()
                    && entry.dimension().equals(level.dimension())) {
                if (restoreIfAvailable(level.getServer(), entry)) {
                    tracker.entries.remove(i);
                    changed = true;
                }
            }
        }
        if (changed) {
            tracker.setDirty();
        }
    }

    /**
     * Tick de maintenance throttlé : ne travaille réellement qu'un appel sur
     * {@value #TICK_THROTTLE}. À enregistrer par l'intégrateur sur
     * {@code ServerTickEvents.END_SERVER_TICK}. Les dimensions non chargées sont ignorées.
     */
    public static void onServerTick(MinecraftServer server) {
        if (++tickCounter % TICK_THROTTLE != 0) {
            return;
        }
        UndergroundRestorationTracker tracker = get(server);
        if (tracker.entries.isEmpty()) {
            return;
        }
        if (tracker.deferOverflowRestorations(server)) {
            tracker.setDirty();
        }
        for (ServerLevel level : server.getAllLevels()) {
            checkDeadlines(level);
        }
    }

    /** Réinitialise le throttle entre deux serveurs ouverts dans la même JVM. */
    public static void onServerStopped(MinecraftServer server) {
        tickCounter = 0;
    }

    /**
     * Restaure immédiatement TOUTES les entrées de la dimension de {@code level},
     * quelle que soit leur échéance (blocs encore intacts uniquement : jamais
     * d'écrasement d'une modification du joueur). Une entrée dont le chunk est
     * indisponible est conservée et rendue exigible pour un prochain tick. Les entrées
     * des autres dimensions sont conservées. À brancher par l'intégrateur sur
     * {@code /penumbra debug restoration}.
     */
    public static void restoreAll(ServerLevel level) {
        UndergroundRestorationTracker tracker = get(level.getServer());
        boolean changed = false;
        long now = gameTime(level);
        for (int i = tracker.entries.size() - 1; i >= 0; i--) {
            Entry entry = tracker.entries.get(i);
            if (entry.dimension().equals(level.dimension())) {
                if (restoreIfAvailable(level.getServer(), entry)) {
                    tracker.entries.remove(i);
                    changed = true;
                } else {
                    Entry deferred = dueNow(entry, now);
                    if (deferred != entry) {
                        tracker.entries.set(i, deferred);
                        changed = true;
                    }
                }
            }
        }
        if (changed) {
            tracker.setDirty();
        }
    }

    /**
     * Essaie de résoudre une restauration. Retourne faux uniquement si la dimension ou
     * le chunk est indisponible, ou si l'écriture a échoué : l'appelant doit alors
     * conserver l'entrée. Si le joueur a déjà modifié le bloc, l'entrée est considérée
     * résolue sans écriture afin de ne jamais écraser sa modification.
     */
    private static boolean restoreIfAvailable(MinecraftServer server, Entry entry) {
        ServerLevel level = server.getLevel(entry.dimension());
        if (level == null || !level.isLoaded(entry.pos())) {
            return false;
        }
        if (level.getBlockState(entry.pos()) == entry.newState()) {
            return level.setBlock(entry.pos(), entry.oldState(), 3);
        }
        return true;
    }

    /** Copie une entrée en avançant son échéance à maintenant si nécessaire. */
    private static Entry dueNow(Entry entry, long now) {
        if (entry.deadline() >= 0L && entry.deadline() <= now) {
            return entry;
        }
        return new Entry(entry.pos(), entry.dimension(), entry.oldState(), entry.newState(),
                entry.type(), now, entry.player());
    }

    /**
     * Transforme le dépassement du cap en restaurations différées. Les entrées
     * inaccessibles ne sont jamais supprimées ; le cap est donc volontairement souple.
     *
     * @return vrai si la liste ou une échéance a changé
     */
    private boolean deferOverflowRestorations(MinecraftServer server) {
        int overflow = this.entries.size() - MAX_ENTRIES;
        if (overflow <= 0) {
            return false;
        }
        boolean changed = false;
        long now = server.overworld().getGameTime();
        for (int i = 0; i < this.entries.size() && overflow > 0; ) {
            Entry entry = this.entries.get(i);
            if (restoreIfAvailable(server, entry)) {
                this.entries.remove(i);
                changed = true;
            } else {
                Entry deferred = dueNow(entry, now);
                if (deferred != entry) {
                    this.entries.set(i, deferred);
                    changed = true;
                }
                i++;
            }
            overflow--;
        }
        return changed;
    }
}
