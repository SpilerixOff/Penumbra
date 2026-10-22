package fr.penumbra.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registre persistant des structures Penumbra générées (SavedData du monde,
 * même pattern Codec + SavedDataType que {@code PenumbraPersistentState}).
 * <p>
 * Chaque entrée mémorise le type, la position d'ancre, la dimension, la
 * variante et un drapeau d'activation (rituel du sanctuaire). Le registre est
 * borné à {@value #MAX_ENTRIES} entrées (éviction FIFO) ; les découvertes par
 * joueur sont bornées à {@value #MAX_DISCOVERY_PLAYERS} joueurs ×
 * {@value #MAX_DISCOVERIES_PER_PLAYER} structures.
 * </p>
 */
public class StructureTracker extends SavedData {

    /** Nom du fichier de données dans le répertoire du monde. */
    private static final String DATA_NAME = "penumbra_structures";
    /** Nombre maximal de structures mémorisées (éviction FIFO). */
    public static final int MAX_ENTRIES = 512;
    /** Nombre maximal de joueurs suivis pour la découverte. */
    private static final int MAX_DISCOVERY_PLAYERS = 256;
    /** Nombre maximal de structures marquées découvertes par joueur. */
    private static final int MAX_DISCOVERIES_PER_PLAYER = 128;

    /**
     * Entrée de structure : type (id), position d'ancre, dimension, variante
     * et drapeau d'activation du rituel (sanctuaires uniquement).
     */
    public record Entry(String type, BlockPos pos, ResourceKey<Level> dimension, int variant,
                        boolean sanctumActivated) {

        /** Codec d'une entrée ; les champs ajoutés après coup restent optionnels. */
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("type").forGetter(Entry::type),
                BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Entry::dimension),
                Codec.INT.optionalFieldOf("variant", 0).forGetter(Entry::variant),
                Codec.BOOL.optionalFieldOf("sanctumActivated", false).forGetter(Entry::sanctumActivated)
        ).apply(instance, Entry::new));

        /** Clé de découverte stable (dimension + position d'ancre). */
        public String discoveryKey() {
            return this.dimension.identifier() + "@" + this.pos.getX() + "," + this.pos.getY() + "," + this.pos.getZ();
        }
    }

    /** Codec complet du registre (toutes les données optionnelles avec défauts). */
    public static final Codec<StructureTracker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of())
                    .forGetter(tracker -> List.copyOf(tracker.entries)),
            Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), Codec.STRING.listOf())
                    .optionalFieldOf("discoveries", Map.of())
                    .forGetter(StructureTracker::discoverySnapshot)
    ).apply(instance, StructureTracker::new));

    // Persistance 26.1 (vérifié dans les sources 26.1.2) : SavedDataType est un record
    // (Identifier, Supplier, Codec, DataFixTypes) ; DataFixTypes peut être null pour un mod.
    private static final SavedDataType<StructureTracker> TYPE =
            new SavedDataType<>(PenumbraMod.id(DATA_NAME), StructureTracker::new, CODEC, null);

    private final List<Entry> entries;
    /** Découvertes par joueur (LinkedHashMap : ordre d'insertion pour l'éviction FIFO). */
    private final Map<UUID, Set<String>> discoveries;

    /** Registre vierge (nouveau monde). */
    public StructureTracker() {
        this(List.of(), Map.of());
    }

    /** Constructeur utilisé par le Codec (valeurs déjà défautées). */
    private StructureTracker(List<Entry> entries, Map<UUID, List<String>> discoveries) {
        this.entries = new ArrayList<>(Math.min(entries.size(), MAX_ENTRIES));
        for (Entry entry : entries) {
            if (this.entries.size() >= MAX_ENTRIES) {
                break;
            }
            this.entries.add(entry);
        }
        this.discoveries = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<String>> playerEntry : discoveries.entrySet()) {
            if (this.discoveries.size() >= MAX_DISCOVERY_PLAYERS) {
                break;
            }
            List<String> keys = playerEntry.getValue();
            Set<String> set = new HashSet<>(keys.subList(0, Math.min(keys.size(), MAX_DISCOVERIES_PER_PLAYER)));
            this.discoveries.put(playerEntry.getKey(), set);
        }
    }

    /** Récupère le registre du monde, en le créant vierge si nécessaire. */
    public static StructureTracker get(MinecraftServer server) {
        // 26.1 : SavedDataStorage.computeIfAbsent(SavedDataType) — un seul argument.
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Vue immuable des découvertes pour le Codec. */
    private Map<UUID, List<String>> discoverySnapshot() {
        Map<UUID, List<String>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : this.discoveries.entrySet()) {
            snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
        }
        return snapshot;
    }

    /** Enregistre une structure nouvellement construite (éviction FIFO au-delà du cap). */
    public void record(StructureType type, BlockPos pos, ServerLevel level, int variant) {
        for (Entry entry : this.entries) {
            if (entry.dimension().equals(level.dimension()) && entry.pos().equals(pos)) {
                return;
            }
        }
        if (this.entries.size() >= MAX_ENTRIES) {
            this.entries.remove(0);
        }
        this.entries.add(new Entry(type.id(), pos.immutable(), level.dimension(), variant, false));
        setDirty();
    }

    /** Toutes les entrées, toutes dimensions confondues (copie défensive). */
    public List<Entry> all() {
        return List.copyOf(this.entries);
    }

    /**
     * Structure la plus proche du joueur dans sa dimension, filtrée par type
     * si {@code type} est non nul. Coût borné (≤ {@value #MAX_ENTRIES} entrées).
     */
    public Optional<Entry> nearest(ServerPlayer player, @Nullable StructureType type) {
        ResourceKey<Level> dimension = player.level().dimension();
        Entry best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entry entry : this.entries) {
            if (!entry.dimension().equals(dimension)) {
                continue;
            }
            if (type != null && !entry.type().equals(type.id())) {
                continue;
            }
            double distance = entry.pos().distSqr(player.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entry;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Vrai si le joueur a déjà découvert cette structure. */
    public boolean hasDiscovered(UUID playerId, Entry entry) {
        Set<String> keys = this.discoveries.get(playerId);
        return keys != null && keys.contains(entry.discoveryKey());
    }

    /** Mémorise la découverte d'une structure par un joueur (maps bornées). */
    public void markDiscovered(UUID playerId, Entry entry) {
        Set<String> keys = this.discoveries.get(playerId);
        if (keys == null) {
            if (this.discoveries.size() >= MAX_DISCOVERY_PLAYERS) {
                this.discoveries.remove(this.discoveries.keySet().iterator().next());
            }
            keys = new HashSet<>();
            this.discoveries.put(playerId, keys);
        }
        if (keys.size() < MAX_DISCOVERIES_PER_PLAYER) {
            keys.add(entry.discoveryKey());
        }
        setDirty();
    }

    /**
     * Sanctuaire non encore activé dont le pilier central est cliqué :
     * le pilier fait 1×1×3 à partir de l'ancre, donc on accepte |dx|≤1,
     * |dz|≤1 et -1≤dy≤3 autour de l'ancre.
     */
    public Optional<Entry> findSanctumAt(UUID playerId, ResourceKey<Level> dimension, BlockPos clicked) {
        return findSanctumAt(playerId, dimension, clicked, false);
    }

    /** A final confrontation can be retried on a previously used altar. */
    public Optional<Entry> findSanctumAt(UUID playerId, ResourceKey<Level> dimension,
                                       BlockPos clicked, boolean allowCompleted) {
        for (Entry entry : this.entries) {
            if (!entry.type().equals(StructureType.ASH_SANCTUM.id())
                    || !entry.dimension().equals(dimension)
                    || !allowCompleted && hasCompletedRitual(playerId, entry)) {
                continue;
            }
            int dx = Math.abs(clicked.getX() - entry.pos().getX());
            int dz = Math.abs(clicked.getZ() - entry.pos().getZ());
            int dy = clicked.getY() - entry.pos().getY();
            if (dx <= 1 && dz <= 1 && dy >= -1 && dy <= 3) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    /** Marque le sanctuaire comme activé (rituel consommé, définitif). */
    public void markSanctumActivated(UUID playerId, Entry entry) {
        Set<String> keys = this.discoveries.get(playerId);
        if (keys == null) {
            if (this.discoveries.size() >= MAX_DISCOVERY_PLAYERS) {
                this.discoveries.remove(this.discoveries.keySet().iterator().next());
            }
            keys = new HashSet<>();
            this.discoveries.put(playerId, keys);
        }
        if (keys.size() < MAX_DISCOVERIES_PER_PLAYER && keys.add(ritualKey(entry))) {
            setDirty();
        }
    }

    private boolean hasCompletedRitual(UUID playerId, Entry entry) {
        Set<String> keys = this.discoveries.get(playerId);
        return keys != null && keys.contains(ritualKey(entry));
    }

    private static String ritualKey(Entry entry) {
        return "ritual@" + entry.discoveryKey();
    }
}
