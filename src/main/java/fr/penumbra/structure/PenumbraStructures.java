package fr.penumbra.structure;

import fr.penumbra.PenumbraMod;
import fr.penumbra.badge.PenumbraBadges;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.entity.watcher.WatcherEntity;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.story.ConfrontationManager;
import fr.penumbra.story.StoryManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Point d'entrée du système de structures Penumbra (Phase 3).
 * <p>
 * Génération : unique point d'entrée {@link ServerChunkEvents#CHUNK_GENERATE}
 * (vérifié en 26.1.2 : signature {@code (ServerLevel, LevelChunk)}) — seuls
 * des NOUVEAUX chunks sont concernés, aucune construction de joueur ne peut
 * exister dans un chunk en cours de génération, donc jamais de destruction.
 * </p>
 * <p>
 * Espacement déterministe : {@code structureRarityChunks} représente la surface
 * moyenne visée (défaut 900 chunks, donc cellule d'environ 30 × 30). Chaque
 * cellule contient exactement UN chunk candidat, choisi par hash
 * reproductible de {@code (worldSeed, cellX, cellZ, "penumbra_structure")} ;
 * une seule structure par cellule, rien n'est placé si le site est refusé.
 * </p>
 */
public final class PenumbraStructures {

    /** Sel du hash de génération (reproductibilité monde). */
    private static final String HASH_SALT = "penumbra_structure";
    /** Rayon² de découverte d'une structure par un joueur (24 blocs). */
    private static final double DISCOVERY_RADIUS_SQR = 24.0 * 24.0;
    /** Cadence du tick de découverte (1 tick sur 100 = toutes les 5 s). */
    private static final int TICK_INTERVAL = 100;
    /** Pente maximale tolérée entre les coins du site (blocs). */
    private static final int MAX_SLOPE = 4;
    /** Hauteur maximale de construction acceptée. */
    private static final int MAX_SURFACE_Y = 180;
    /** Constructions différées traitées par tick, afin de borner le travail serveur. */
    private static final int MAX_PENDING_PER_TICK = 2;
    /** Taille maximale de la file, y compris avec un prégénérateur de monde. */
    private static final int MAX_PENDING_QUEUE = 512;
    /** Un chunk dispose de dix secondes pour devenir accessible dans le cache serveur. */
    private static final int MAX_PENDING_WAIT_TICKS = 200;

    /**
     * Le hook CHUNK_GENERATE s'exécute avant la publication du LevelChunk dans
     * ServerChunkCache. Tout accès via ServerLevel (même au chunk courant) peut
     * alors attendre sa propre future et bloquer le serveur. Le callback ne fait
     * donc qu'enregistrer une intention pure ; la construction a lieu en fin de
     * tick, lorsque getChunkNow confirme que le chunk est réellement accessible.
     */
    private static final Queue<PendingStructure> PENDING_GENERATION = new ConcurrentLinkedQueue<>();
    private static final Set<Long> PENDING_CHUNKS = ConcurrentHashMap.newKeySet();
    private static final Set<java.util.UUID> PENDING_RITUALS = new java.util.HashSet<>();

    private static int tickCounter;

    private PenumbraStructures() {
    }

    /**
     * Enregistre le hook de génération et le callback de rituel du sanctuaire.
     * Appelé par l'intégrateur depuis {@code PenumbraMod.onInitialize} ;
     * {@link #onServerTick} est enregistré séparément sur
     * {@code ServerTickEvents.END_SERVER_TICK}.
     */
    public static void register() {
        ServerChunkEvents.CHUNK_GENERATE.register(PenumbraStructures::onChunkGenerate);
        UseBlockCallback.EVENT.register(PenumbraStructures::onUseBlock);
    }

    public static void onServerStopped(MinecraftServer server) {
        tickCounter = 0;
        PENDING_GENERATION.clear();
        PENDING_CHUNKS.clear();
        PENDING_RITUALS.clear();
    }

    // ---------------------------------------------------------------------
    // Génération (nouveaux chunks uniquement)
    // ---------------------------------------------------------------------

    private static void onChunkGenerate(ServerLevel level, LevelChunk chunk) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.structuresEnabled) {
            return;
        }
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        int spacing = cellSideForRarity(config.structureRarityChunks);
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();
        int cellX = Math.floorDiv(chunkX, spacing);
        int cellZ = Math.floorDiv(chunkZ, spacing);
        long seed = level.getSeed();

        // Un unique chunk candidat par cellule, reproductible.
        int candidateX = cellX * spacing
                + (int) Long.remainderUnsigned(hash(seed, cellX, cellZ, HASH_SALT), spacing);
        int candidateZ = cellZ * spacing
                + (int) Long.remainderUnsigned(hash(seed, cellX, cellZ, HASH_SALT + ":z"), spacing);
        if (chunkX != candidateX || chunkZ != candidateZ) {
            return;
        }

        StructureType type = StructureType.pick(
                (int) Long.remainderUnsigned(hash(seed, candidateX, candidateZ, HASH_SALT + ":type"), 100));
        int variant = (int) Long.remainderUnsigned(
                hash(seed, candidateX, candidateZ, HASH_SALT + ":variant"), type.variantCount());

        if (PENDING_GENERATION.size() >= MAX_PENDING_QUEUE) {
            return;
        }
        long key = chunkKey(chunkX, chunkZ);
        if (PENDING_CHUNKS.add(key)) {
            PENDING_GENERATION.offer(new PendingStructure(
                    chunkX, chunkZ, type, variant, seed,
                    level.getGameTime() + MAX_PENDING_WAIT_TICKS));
        }
    }

    /** Clé compacte et stable pour dédupliquer les callbacks d'un même chunk. */
    static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFF_FFFFL);
    }

    /**
     * Termine au plus quelques constructions différées, sans jamais charger
     * un chunk. Un chunk pas encore publié est simplement retenté au tick
     * suivant ; passé dix secondes, l'intention est abandonnée proprement.
     */
    private static void processPendingStructures(MinecraftServer server) {
        if (!PenumbraConfig.get().structuresEnabled) {
            PENDING_GENERATION.clear();
            PENDING_CHUNKS.clear();
            return;
        }
        int due = Math.min(MAX_PENDING_PER_TICK, PENDING_GENERATION.size());
        ServerLevel level = server.overworld();
        for (int i = 0; i < due; i++) {
            PendingStructure pending = PENDING_GENERATION.poll();
            if (pending == null) {
                return;
            }
            long key = chunkKey(pending.chunkX(), pending.chunkZ());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pending.chunkX(), pending.chunkZ());
            if (chunk == null) {
                if (level.getGameTime() <= pending.expiresAtTick()) {
                    PENDING_GENERATION.offer(pending);
                } else {
                    PENDING_CHUNKS.remove(key);
                    PenumbraMod.LOGGER.warn(
                            "Structure ignorée : le chunk [{}, {}] n'est pas devenu accessible après {} ticks",
                            pending.chunkX(), pending.chunkZ(), MAX_PENDING_WAIT_TICKS);
                }
                continue;
            }

            try {
                int x = chunk.getPos().getMiddleBlockX();
                int z = chunk.getPos().getMiddleBlockZ();
                BlockPos surface = findSuitableSurface(level, chunk, x, z);
                if (surface != null) {
                    BlockPos anchor = build(pending.type(), level, surface, pending.variant(), pending.seed());
                    if (anchor != null) {
                        StructureTracker.get(server).record(
                                pending.type(), anchor, level, pending.variant());
                    }
                }
            } catch (RuntimeException exception) {
                // Une structure optionnelle ne doit jamais faire tomber le serveur.
                PenumbraMod.LOGGER.error(
                        "Impossible de générer la structure {} dans le chunk [{}, {}]",
                        pending.type(), pending.chunkX(), pending.chunkZ(), exception);
            } finally {
                PENDING_CHUNKS.remove(key);
            }
        }
    }

    /** Convertit une rareté exprimée en surface moyenne en côté de cellule. */
    static int cellSideForRarity(int rarityAreaChunks) {
        return Math.max(8, (int) Math.round(Math.sqrt(Math.max(64, rarityAreaChunks))));
    }

    /**
     * Site de surface valide, ou {@code null} (biome aquatique, altitude
     * aberrante, pente raide, eau ou sol non solide). Les échantillons restent
     * à ±6 blocs du centre du chunk, donc DANS le chunk en génération :
     * aucun chargement de chunk forcé.
     */
    private static BlockPos findSuitableSurface(ServerLevel level, LevelChunk chunk, int x, int z) {
        int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        if (y <= level.getSeaLevel() + 1 || y > MAX_SURFACE_Y) {
            return null;
        }
        BlockPos ground = new BlockPos(x, y - 1, z);
        Holder<Biome> biome = chunk.getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(ground.getY()), QuartPos.fromBlock(z));
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN)
                || biome.is(BiomeTags.IS_RIVER)) {
            return null;
        }
        int min = y;
        int max = y;
        for (int[] corner : new int[][]{{-6, -6}, {6, -6}, {-6, 6}, {6, 6}}) {
            int cornerY = chunk.getHeight(
                    Heightmap.Types.WORLD_SURFACE, x + corner[0], z + corner[1]) + 1;
            min = Math.min(min, cornerY);
            max = Math.max(max, cornerY);
        }
        if (max - min > MAX_SLOPE) {
            return null;
        }
        if (!chunk.getBlockState(ground).getFluidState().isEmpty()
                || !chunk.getBlockState(ground.above()).getFluidState().isEmpty()) {
            return null;
        }
        if (!chunk.getBlockState(ground).isSolid()) {
            return null;
        }
        return new BlockPos(x, y, z);
    }

    private record PendingStructure(int chunkX, int chunkZ, StructureType type,
                                    int variant, long seed, long expiresAtTick) {
    }

    /**
     * Construit une structure à la surface donnée et retourne son ancre.
     * Partagé entre la génération naturelle et {@code /penumbra structure spawn}.
     */
    public static BlockPos build(StructureType type, ServerLevel level, BlockPos surface,
                                 int variant, long seed) {
        return switch (type) {
            case OBSERVATION_POST -> ObservationPostBuilder.build(level, surface, variant, seed);
            case SHIFTED_HOUSE -> ShiftedHouseBuilder.build(level, surface, variant, seed);
            case PENUMBRA_WELL -> PenumbraWellBuilder.build(level, surface, variant, seed);
            case ASH_SANCTUM -> AshSanctumBuilder.build(level, surface, variant, seed);
            // Le Bûcher n'a ni variante ni génération naturelle : son site est
            // dérivé de la graine du monde et posé par PyreManager. On le câble
            // quand même ici pour que /penumbra structure spawn signal_pyre
            // fonctionne, et parce qu'un switch exhaustif sur l'énumération est
            // ce qui garantit qu'aucune structure future ne sera oubliée.
            case SIGNAL_PYRE -> SignalPyreBuilder.build(level, surface, seed);
        };
    }

    /**
     * Hash 64 bits déterministe (mixeur type splitmix) de la graine, d'une
     * coordonnée de cellule et d'un sel texte. Garantit des décisions
     * reproductibles à monde identique.
     */
    static long hash(long seed, long a, long b, String salt) {
        long h = seed ^ (long) salt.hashCode() * 0x9E3779B97F4A7C15L;
        h ^= (a + 0x9E3779B97F4A7C15L) * 0xBF58476D1CE4E5B9L;
        h ^= (b + 0xC2B2AE3D27D4EB4FL) * 0x94D049BB133111EBL;
        h ^= h >>> 30;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        return h;
    }

    // ---------------------------------------------------------------------
    // Découverte (tick serveur throttlé) et rituel du sanctuaire
    // ---------------------------------------------------------------------

    /**
     * Tick serveur throttlé (1/{@value #TICK_INTERVAL}) : première approche
     * d'une structure connue à moins de 24 blocs → note narrative
     * {@code structure_<type>} + message discret. Coût borné : ≤ 512 entrées
     * du tracker, filtrées par dimension, une fois toutes les 5 secondes.
     */
    public static void onServerTick(MinecraftServer server) {
        processPendingStructures(server);
        if (++tickCounter % TICK_INTERVAL != 0) {
            return;
        }
        if (!PenumbraConfig.get().structuresEnabled) {
            return;
        }
        StructureTracker tracker = StructureTracker.get(server);
        java.util.List<StructureTracker.Entry> entries = tracker.all();
        if (entries.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (StructureTracker.Entry entry : entries) {
                if (!entry.dimension().equals(player.level().dimension())) {
                    continue;
                }
                if (tracker.hasDiscovered(player.getUUID(), entry)) {
                    continue;
                }
                if (entry.pos().distSqr(player.blockPosition()) <= DISCOVERY_RADIUS_SQR) {
                    tracker.markDiscovered(player.getUUID(), entry);
                    StoryManager.noteDiscovery(player, "structure_" + entry.type());
                    // Phase 4 : badge « Traces de pas » (idempotent côté avancements).
                    PenumbraBadges.grant(player, "first_structure");
                    player.sendSystemMessage(Component.translatable("penumbra.structure.found"));
                }
            }
        }
    }

    /**
     * Rituel du sanctuaire : clic droit avec un {@code ash_sigil} sur le
     * pilier central d'un sanctuaire non encore accompli par ce joueur → l'objet
     * est consommé et la finale est jouée. L'activation est individuelle : un
     * sanctuaire partagé ne bloque plus la progression des autres joueurs.
     */
    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
                                                BlockHitResult hitResult) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(PenumbraItems.ASH_SIGIL)) {
            return InteractionResult.PASS;
        }
        if (PENDING_RITUALS.contains(player.getUUID())
                || ConfrontationManager.isActive(player.getUUID())) {
            serverPlayer.sendOverlayMessage(Component.translatable("penumbra.ritual.busy"));
            return InteractionResult.SUCCESS;
        }
        boolean retryFinale = StoryManager.getStage(serverPlayer)
                >= fr.penumbra.story.StoryStage.CONFRONTATION.index()
                && !ConfrontationManager.isWon(serverPlayer);
        StructureTracker tracker = StructureTracker.get(serverLevel.getServer());
        Optional<StructureTracker.Entry> sanctum =
                tracker.findSanctumAt(serverPlayer.getUUID(), serverLevel.dimension(),
                        hitResult.getBlockPos(), retryFinale);
        if (sanctum.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        BlockPos pos = hitResult.getBlockPos();
        tracker.markSanctumActivated(serverPlayer.getUUID(), sanctum.get());
        PENDING_RITUALS.add(player.getUUID());
        // Covers the 13-second prelude; also expires on an interrupted sequence.
        java.util.UUID ritualPlayer = player.getUUID();
        HorrorDirector.runLater(serverLevel, 300, () -> PENDING_RITUALS.remove(ritualPlayer));
        runRitualFinale(serverLevel, serverPlayer, pos);
        return InteractionResult.SUCCESS;
    }

    // ================= 0.5.0 : finale du rituel =================

    /** Rayon de dissolution des entités d'horreur au début du rituel. */
    private static final double RITUAL_PURGE_RADIUS = 64.0;
    /** Durée de l'accalmie post-rituel (2 jours de jeu — puis il réapprend). */
    private static final long RITUAL_CALM_TICKS = 48000L;

    /**
     * Finale scriptée du Sanctuaire (0.5.0) — l'ancien rituel jouait un drone et
     * affichait un message ; la collecte des fragments méritait un point d'orgue.
     * Séquence (DelayedTaskScheduler, ~16 s), conçue comme une inversion des
     * règles apprises : de tout le mod, l'Hôte n'est JAMAIS resté visible face
     * au joueur — ici, une seule fois, il vient se montrer.
     * <ol>
     *   <li>t0 : toute entité d'horreur à ≤ 64 blocs se dissout en cendre ;
     *       silence total (aucun son : le vide APRÈS le retrait) ;</li>
     *   <li>t+3 s : quatre murmures convergent, un par point cardinal ;</li>
     *   <li>t+7 s : l'Hôte apparaît à 8 blocs FACE au joueur, figé (DORMANT),
     *       et le regarde ;</li>
     *   <li>t+13 s : {@code RITUAL_RELEASE}, dissolution en cendre dense,
     *       tension à zéro, accalmie de 2 jours de jeu
     *       ({@code HorrorDirector.applyPostRitualCalm}), badge et messages.
     *       La découverte {@code sanctum_ritual} fait avancer l'histoire vers
     *       CONFRONTATION par la règle existante (stade 5→6).</li>
     * </ol>
     * Tous les sons passent en priorité (hors budget audio ordinaire ; un
     * doublon strictement identique du même tick peut être fusionné).
     */
    private static void runRitualFinale(ServerLevel level, ServerPlayer player, BlockPos pos) {
        // t0 — retrait : la cendre rappelle la cendre.
        AABB purgeBox = player.getBoundingBox().inflate(RITUAL_PURGE_RADIUS);
        for (HostEntity host : level.getEntitiesOfClass(HostEntity.class, purgeBox)) {
            if (!host.isOwnedBy(player)) {
                continue;
            }
            level.sendParticles(PenumbraParticles.ASH_MOTE,
                    host.getX(), host.getY() + 1.3, host.getZ(), 24, 0.4, 0.8, 0.4, 0.01);
            host.discard();
        }
        for (WatcherEntity watcher : level.getEntitiesOfClass(WatcherEntity.class, purgeBox)) {
            if (!watcher.isOwnedBy(player)) {
                continue;
            }
            level.sendParticles(PenumbraParticles.ASH_MOTE,
                    watcher.getX(), watcher.getY() + 1.2, watcher.getZ(), 24, 0.4, 0.9, 0.4, 0.01);
            watcher.discard();
        }
        player.sendSystemMessage(Component.translatable("penumbra.ritual.silence"));

        // t+3 s — quatre murmures convergents, un par point cardinal.
        java.util.UUID playerId = player.getUUID();
        MinecraftServer server = level.getServer();
        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 2.0 * i;
            double dist = 24.0 - i * 5.0; // 24, 19, 14, 9 blocs : ils approchent
            Vec3 at = player.position().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist);
            HorrorDirector.runLater(level, 60 + i * 25, () -> {
                ServerPlayer target = server.getPlayerList().getPlayer(playerId);
                if (target != null && target.isAlive() && target.level() == level) {
                    PenumbraNetworking.sendSoundPriority(target,
                            PenumbraSounds.whisperVariant(target.getRandom()), at, 0.8f, 0.65f);
                }
            });
        }

        // t+7 s — l'unique face-à-face du mod.
        HorrorDirector.runLater(level, 140, () -> {
            ServerPlayer target = server.getPlayerList().getPlayer(playerId);
            if (target == null || !target.isAlive() || target.level() != level) {
                return;
            }
            Vec3 look = target.getLookAngle();
            Vec3 flat = new Vec3(look.x, 0.0, look.z);
            if (flat.lengthSqr() < 1.0E-4) {
                flat = new Vec3(1.0, 0.0, 0.0);
            }
            flat = flat.normalize();
            // spawnNear apparaît hors champ ; pour la finale, on le replace devant.
            HostEntity host = HostEntity.spawnNear(level, target, 6.0, 10.0);
            double hx = target.getX() + flat.x * 8.0;
            double hz = target.getZ() + flat.z * 8.0;
            BlockPos ritualSpot = findRitualStandingSpot(level, hx, target.getY(), hz);
            if (host != null && ritualSpot != null) {
                host.snapTo(ritualSpot.getX() + 0.5, ritualSpot.getY(), ritualSpot.getZ() + 0.5,
                        (float) (Math.atan2(target.getZ() - hz, target.getX() - hx)
                                * net.minecraft.util.Mth.RAD_TO_DEG) - 90.0f, 0.0f);
                host.setBehavior(fr.penumbra.entity.HostBehavior.DORMANT);
                host.setNoAi(true);
                host.setInvulnerable(true);
                host.setMaxLifetimeTicks(200);
                host.getLookControl().setLookAt(target, 30.0f, 30.0f);
                PenumbraNetworking.sendSoundPriority(target, PenumbraSounds.HEARTBEAT,
                        target.position(), 0.5f, 0.9f);
                // t+13 s — libération.
                java.util.UUID hostId = host.getUUID();
                HorrorDirector.runLater(level, 120, () -> {
                    ServerPlayer end = server.getPlayerList().getPlayer(playerId);
                    if (level.getEntity(hostId) instanceof HostEntity bound && bound.isAlive()) {
                        level.sendParticles(PenumbraParticles.ASH_MOTE,
                                bound.getX(), bound.getY() + 1.3, bound.getZ(),
                                96, 0.5, 1.2, 0.5, 0.02);
                        bound.discard();
                    }
                    if (end != null && end.isAlive() && end.level() == level) {
                        finishRitual(level, end, pos);
                    }
                });
            } else {
                if (host != null) {
                    host.discard();
                }
                // Aucun emplacement valide : la libération arrive sans face-à-face.
                HorrorDirector.runLater(level, 120, () -> {
                    ServerPlayer end = server.getPlayerList().getPlayer(playerId);
                    if (end != null && end.isAlive() && end.level() == level) {
                        finishRitual(level, end, pos);
                    }
                });
            }
        });
    }

    /** Trouve un emplacement debout chargé devant le joueur, sans traverser le décor. */
    private static BlockPos findRitualStandingSpot(ServerLevel level, double x, double y, double z) {
        int baseX = net.minecraft.util.Mth.floor(x);
        int baseY = net.minecraft.util.Mth.floor(y);
        int baseZ = net.minecraft.util.Mth.floor(z);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = 4; dy >= -6; dy--) {
            cursor.set(baseX, baseY + dy, baseZ);
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockPos ground = cursor.below();
            if (level.getBlockState(ground).isSolidRender()
                    && level.getBlockState(cursor).isAir()
                    && level.getBlockState(cursor.above()).isAir()
                    && level.getFluidState(ground).isEmpty()
                    && level.getFluidState(cursor).isEmpty()
                    && level.getFluidState(cursor.above()).isEmpty()) {
                return cursor.immutable();
            }
        }
        return null;
    }

    /** Clôture du rituel : son de libération, accalmie, découverte, badge, messages. */
    private static void finishRitual(ServerLevel level, ServerPlayer player, BlockPos pos) {
        // 1.0 — au stade final, le rituel ne libère plus : il convoque.
        if (ConfrontationManager.tryBegin(level, player, pos)) {
            return;
        }
        if (StoryManager.getStage(player) >= fr.penumbra.story.StoryStage.CONFRONTATION.index()
                && !ConfrontationManager.isWon(player)) {
            player.sendSystemMessage(Component.translatable("penumbra.confrontation.no_space"));
            return; // A failed summon is retryable, not a completed ritual reward.
        }
        PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.RITUAL_RELEASE,
                Vec3.atCenterOf(pos), 1.0f, 1.0f);
        HorrorDirector.applyPostRitualCalm(player, RITUAL_CALM_TICKS);
        StoryManager.noteDiscovery(player, "sanctum_ritual");
        // Phase 4 : badge « Cendre et serment ».
        PenumbraBadges.grant(player, "sanctum_ritual");
        player.sendSystemMessage(Component.translatable("penumbra.structure.ritual_done"));
        // L'épilogue n'est pas une fin : après l'accalmie, il réapprend.
        player.sendSystemMessage(Component.translatable("penumbra.ritual.epilogue"));
    }
}
