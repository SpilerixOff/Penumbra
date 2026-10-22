package fr.penumbra.copy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registre persistant des copies imparfaites construites par l'Hôte
 * (SavedData monde, stockée dans le stockage de l'overworld comme
 * {@code PenumbraPersistentState} — même pattern 26.1.2).
 * <p>
 * Pour chaque copie : joueur, dimension, site (centre au sol), tick de jeu de
 * construction et liste des blocs posés (position + état précédent + état
 * posé, bornée à {@value #MAX_BLOCKS_PER_COPY}). Le registre est borné à
 * {@value #MAX_COPIES} copies. À saturation, la plus ancienne copie dont les
 * chunks sont déjà chargés est restaurée avant toute nouvelle construction ;
 * une copie inaccessible n'est jamais oubliée. Les cooldowns de
 * construction par joueur (tick de la dernière copie) sont également persistés.
 * </p>
 * <p>
 * {@link #restoreAll(ServerLevel)} ne restaure un bloc que si l'état actuel
 * est encore exactement celui posé par la copie : une modification ultérieure
 * du joueur n'est jamais écrasée. Seuls les mannequins étiquetés par Penumbra
 * et posés à moins de {@value #MANNEQUIN_CLEANUP_RADIUS} blocs du site sont
 * retirés avec la copie : les porte-armures du joueur sont toujours préservés.
 * </p>
 */
public class CopyTracker extends SavedData {

    /** Nombre maximal de blocs mémorisés par copie. */
    public static final int MAX_BLOCKS_PER_COPY = 64;
    /** Nombre maximal de copies conservées (éviction FIFO au-delà). */
    public static final int MAX_COPIES = 32;
    /** Rayon de retrait des mannequins autour du centre d'un site restauré. */
    private static final double MANNEQUIN_CLEANUP_RADIUS = 6.0;
    /** Tag persistant distinguant nos mannequins des porte-armures du joueur. */
    static final String MANNEQUIN_TAG = "penumbra.copy_mannequin";
    /** First opportunity after reaching familiarity; failed sites are retried once a minute. */
    static final long FIRST_ATTEMPT_DELAY_TICKS = 2_400L;
    static final long RETRY_DELAY_TICKS = 1_200L;

    private record Attempt(long remainingTicks, long observedTick, int tries) {
        private static final Codec<Attempt> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.optionalFieldOf("remainingTicks", FIRST_ATTEMPT_DELAY_TICKS)
                        .forGetter(Attempt::remainingTicks),
                Codec.LONG.optionalFieldOf("observedTick", 0L).forGetter(Attempt::observedTick),
                Codec.INT.optionalFieldOf("tries", 0).forGetter(Attempt::tries)
        ).apply(instance, Attempt::new));

        private Attempt {
            remainingTicks = Math.max(0L, Math.min(FIRST_ATTEMPT_DELAY_TICKS, remainingTicks));
            observedTick = Math.max(0L, observedTick);
            tries = Math.max(0, tries);
        }

        private Attempt advance(long gameTime) {
            long now = Math.max(0L, gameTime);
            long elapsed = now >= observedTick ? now - observedTick : 0L;
            return new Attempt(Math.max(0L, remainingTicks - elapsed), now, tries);
        }
    }

    /** Bloc posé par une copie : position, état remplacé et état posé. */
    public record PlacedBlock(BlockPos pos, BlockState previousState, BlockState placedState) {
        /** Codec d'un bloc posé (BlockState.CODEC résout les instances canoniques). */
        static final Codec<PlacedBlock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(PlacedBlock::pos),
                BlockState.CODEC.fieldOf("old").forGetter(PlacedBlock::previousState),
                BlockState.CODEC.fieldOf("placed").forGetter(PlacedBlock::placedState)
        ).apply(instance, PlacedBlock::new));
    }

    /** Copie construite : propriétaire, dimension, site, date et blocs posés. */
    private record CopyRecord(UUID player, ResourceKey<Level> dimension, BlockPos site,
                              long gameTime, List<PlacedBlock> blocks, boolean discovered) {
        /** Codec d'une copie (sous-structure : champs obligatoires). */
        static final Codec<CopyRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.xmap(UUID::fromString, UUID::toString).fieldOf("player").forGetter(CopyRecord::player),
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(CopyRecord::dimension),
                BlockPos.CODEC.fieldOf("site").forGetter(CopyRecord::site),
                Codec.LONG.fieldOf("time").forGetter(CopyRecord::gameTime),
                PlacedBlock.CODEC.listOf().fieldOf("blocks").forGetter(CopyRecord::blocks),
                Codec.BOOL.optionalFieldOf("discovered", false).forGetter(CopyRecord::discovered)
        ).apply(instance, CopyRecord::new));
    }

    /** Codec complet du registre (champs optionnels avec défauts, tolérant aux sauvegardes partielles). */
    public static final Codec<CopyTracker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CopyRecord.CODEC.listOf().optionalFieldOf("copies", List.of())
                    .forGetter(tracker -> List.copyOf(tracker.copies)),
            Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("cooldowns", Map.of())
                    .forGetter(tracker -> tracker.cooldowns),
            Codec.unboundedMap(Codec.STRING, Attempt.CODEC).optionalFieldOf("attempts", Map.of())
                    .forGetter(tracker -> tracker.attempts)
    ).apply(instance, CopyTracker::new));

    // Persistance 26.1 (vérifié dans les sources 26.1.2) : SavedDataType est un record
    // top-level (Identifier, Supplier, Codec, DataFixTypes) ; DataFixTypes null pour un mod.
    private static final SavedDataType<CopyTracker> TYPE =
            new SavedDataType<>(PenumbraMod.id("copies"), CopyTracker::new, CODEC, null);

    private final List<CopyRecord> copies;
    private final Map<String, Long> cooldowns;
    private final Map<String, Attempt> attempts;

    /** Registre vierge (nouveau monde). */
    public CopyTracker() {
        this(List.of(), Map.of(), Map.of());
    }

    /** Constructeur utilisé par le Codec. */
    private CopyTracker(List<CopyRecord> copies, Map<String, Long> cooldowns, Map<String, Attempt> attempts) {
        this.copies = new ArrayList<>();
        int first = Math.max(0, copies.size() - MAX_COPIES);
        for (int i = first; i < copies.size(); i++) {
            CopyRecord copy = copies.get(i);
            List<PlacedBlock> blocks = copy.blocks().size() > MAX_BLOCKS_PER_COPY
                    ? List.copyOf(copy.blocks().subList(0, MAX_BLOCKS_PER_COPY))
                    : List.copyOf(copy.blocks());
            this.copies.add(new CopyRecord(copy.player(), copy.dimension(), copy.site(),
                    copy.gameTime(), blocks, copy.discovered()));
        }
        this.cooldowns = new HashMap<>();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            try {
                UUID.fromString(entry.getKey());
                this.cooldowns.put(entry.getKey(), Math.max(0L, entry.getValue()));
            } catch (IllegalArgumentException ignored) {
                // Entrée corrompue ignorée sans invalider toute la sauvegarde.
            }
        }
        this.attempts = new HashMap<>();
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            try {
                UUID.fromString(entry.getKey());
                this.attempts.put(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // Only actual player UUIDs belong in the persistent schedule.
            }
        }
    }

    /**
     * Récupère le registre des copies, en le créant vierge si nécessaire.
     * Le registre vit dans le stockage de l'overworld ; chaque copie mémorise
     * sa dimension d'origine pour une restauration ciblée par niveau.
     */
    public static CopyTracker get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * Enregistre une copie nouvellement construite. La liste de blocs est
     * bornée à {@value #MAX_BLOCKS_PER_COPY} (les blocs les plus anciennement
     * posés, en tête, sont conservés en priorité : la troncation retire la fin)
     * et le registre est borné à {@value #MAX_COPIES} copies (éviction FIFO).
     */
    public void record(ServerLevel level, UUID playerId, BlockPos site, List<PlacedBlock> blocks) {
        List<PlacedBlock> trimmed = blocks.size() > MAX_BLOCKS_PER_COPY
                ? List.copyOf(blocks.subList(0, MAX_BLOCKS_PER_COPY))
                : List.copyOf(blocks);
        this.copies.add(new CopyRecord(playerId, level.dimension(), site.immutable(),
                level.getGameTime(), trimmed, false));
        setDirty();
    }

    /**
     * Réserve une place avant une construction. Si le registre est plein, restaure
     * la plus ancienne copie actuellement accessible. Retourne faux lorsque toutes
     * les anciennes copies sont dans des chunks déchargés : la nouvelle construction
     * doit alors être différée plutôt que de créer des blocs impossibles à restaurer.
     */
    public boolean prepareForNewCopy(ServerLevel currentLevel) {
        if (this.copies.size() < MAX_COPIES) {
            return true;
        }
        for (int i = 0; i < this.copies.size(); i++) {
            CopyRecord copy = this.copies.get(i);
            ServerLevel level = currentLevel.getServer().getLevel(copy.dimension());
            if (level != null && restoreCopyIfLoaded(level, copy)) {
                this.copies.remove(i);
                setDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * Restaure tous les blocs des copies de la dimension du niveau donné.
     * Un bloc n'est restauré que si son état actuel est encore celui posé par
     * la copie (identité d'instances canoniques après résolution du codec) ;
     * toute modification ultérieure du joueur est préservée. Le contenu des
     * conteneurs factices (coffre « imitation d'inventaire ») est purgé avant
     * retrait pour ne rien disperser au sol. Les copies traitées sont oubliées ;
     * celles dont les chunks sont déchargés restent suivies pour un prochain appel.
     *
     * @return nombre de copies restaurées
     */
    public int restoreAll(ServerLevel level) {
        int restored = 0;
        Iterator<CopyRecord> iterator = this.copies.iterator();
        while (iterator.hasNext()) {
            CopyRecord copy = iterator.next();
            if (!copy.dimension().equals(level.dimension())) {
                continue;
            }
            if (!restoreCopyIfLoaded(level, copy)) {
                continue;
            }
            iterator.remove();
            restored++;
        }
        if (restored > 0) {
            setDirty();
        }
        return restored;
    }

    /** Restaure une copie sans jamais charger de chunk ; faux si elle est inaccessible. */
    private static boolean restoreCopyIfLoaded(ServerLevel level, CopyRecord copy) {
        if (!level.isLoaded(copy.site())) {
            return false;
        }
        for (PlacedBlock placed : copy.blocks()) {
            if (!level.isLoaded(placed.pos())) {
                return false;
            }
        }
        List<PlacedBlock> blocks = copy.blocks();
        // Ordre inverse de pose : les blocs dépendants (moitié haute de porte,
        // torche, panneau) sont retirés avant leur support.
        for (int i = blocks.size() - 1; i >= 0; i--) {
            PlacedBlock placed = blocks.get(i);
            if (!level.getBlockState(placed.pos()).equals(placed.placedState())) {
                continue; // le joueur a modifié ce bloc depuis : ne jamais écraser
            }
            if (level.getBlockEntity(placed.pos()) instanceof Container container) {
                container.clearContent(); // purge l'inventaire factice avant retrait
            }
            level.setBlock(placed.pos(), placed.previousState(), Block.UPDATE_ALL);
        }
        for (ArmorStand stand : level.getEntities(EntityTypeTest.forClass(ArmorStand.class),
                new AABB(copy.site()).inflate(MANNEQUIN_CLEANUP_RADIUS),
                stand -> stand.isAlive() && stand.entityTags().contains(MANNEQUIN_TAG))) {
            stand.discard();
        }
        return true;
    }

    /** Nombre de copies enregistrées pour ce joueur (toutes dimensions). */
    /**
     * Sites des copies encore debout de ce joueur, dans cette dimension
     * (0.16.0 : sert au moment « l'occupant » — quelque chose dort dans la copie).
     */
    public List<BlockPos> sitesFor(UUID playerId, ResourceKey<Level> dimension) {
        List<BlockPos> sites = new ArrayList<>();
        for (CopyRecord copy : copies) {
            if (copy.player().equals(playerId) && copy.dimension() == dimension) {
                sites.add(copy.site());
            }
        }
        return sites;
    }

    public int count(UUID playerId) {
        int total = 0;
        for (CopyRecord copy : this.copies) {
            if (copy.player().equals(playerId)) {
                total++;
            }
        }
        return total;
    }

    /** Tick de jeu de la dernière copie construite pour ce joueur (0 si jamais). */
    public long lastCopyTick(UUID playerId) {
        return this.cooldowns.getOrDefault(playerId.toString(), 0L);
    }

    public boolean cooldownElapsed(UUID playerId, long overworldGameTime) {
        Long last = this.cooldowns.get(playerId.toString());
        if (last == null) {
            return true;
        }
        long now = Math.max(0L, overworldGameTime);
        if (now < last) {
            // A restored world clock must not lock a player out indefinitely.
            this.cooldowns.put(playerId.toString(), now);
            setDirty();
            return false;
        }
        return now - last >= CopyManager.COOLDOWN_TICKS;
    }

    /** Claims one bounded attempt; reconnecting never starts a fresh waiting period. */
    public boolean claimAttempt(UUID playerId, long overworldGameTime) {
        String id = playerId.toString();
        long now = Math.max(0L, overworldGameTime);
        Attempt previous = this.attempts.get(id);
        Attempt current = previous == null
                ? new Attempt(FIRST_ATTEMPT_DELAY_TICKS, now, 0) : previous.advance(now);
        boolean due = current.remainingTicks() == 0L;
        if (due) {
            current = new Attempt(RETRY_DELAY_TICKS, now,
                    current.tries() == Integer.MAX_VALUE ? Integer.MAX_VALUE : current.tries() + 1);
        }
        if (!current.equals(previous)) {
            this.attempts.put(id, current);
            setDirty();
        }
        return due;
    }

    /** Explicit administrative reset; ordinary disconnects leave this schedule intact. */
    public void resetAttempts(UUID playerId) {
        if (this.attempts.remove(playerId.toString()) != null) {
            setDirty();
        }
    }

    /** Enregistre le tick de construction d'une copie (départ du cooldown). */
    public void noteCopyBuilt(UUID playerId, long gameTime) {
        this.cooldowns.put(playerId.toString(), gameTime);
        this.attempts.put(playerId.toString(), new Attempt(FIRST_ATTEMPT_DELAY_TICKS, gameTime, 0));
        setDirty();
    }

    /** Marque la première copie que son propriétaire approche réellement. */
    public boolean markDiscoveredNear(ServerPlayer player, double radius) {
        double radiusSqr = radius * radius;
        for (int i = 0; i < this.copies.size(); i++) {
            CopyRecord copy = this.copies.get(i);
            if (copy.discovered() || !copy.player().equals(player.getUUID())
                    || !copy.dimension().equals(player.level().dimension())
                    || copy.site().distSqr(player.blockPosition()) > radiusSqr) {
                continue;
            }
            this.copies.set(i, new CopyRecord(copy.player(), copy.dimension(), copy.site(),
                    copy.gameTime(), copy.blocks(), true));
            setDirty();
            return true;
        }
        return false;
    }
}
