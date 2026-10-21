package fr.penumbra.journal;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.structure.SignalPyreBuilder;
import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.HashMap;
import java.util.Map;

/**
 * Le Bûcher (0.18.0) — l'unique feu de signal du monde, et la colonne de fumée
 * qui le trahit de très loin.
 *
 * <p><b>Un seul par monde.</b> Sa position est dérivée de la graine, à
 * {@value #MIN_DISTANCE}–{@value #MAX_DISTANCE} blocs de l'origine : deux
 * joueurs de la même carte le trouveront exactement au même endroit, et un
 * monde donné n'en aura jamais deux.</p>
 *
 * <p><b>La fumée existe avant la structure.</b> C'est le point clé : le panache
 * est émis en altitude ({@value #PLUME_BOTTOM} à {@value #PLUME_TOP}) et
 * diffusé en mode forcé, donc visible jusqu'à 512 blocs — bien avant que le
 * chunk du sol ne soit chargé. Le joueur voit d'abord une colonne de fumée à
 * l'horizon, marche vers elle, et ce n'est qu'à {@value #BUILD_DISTANCE} blocs
 * que le camp se construit réellement sous ses pieds. L'inverse — attendre
 * d'être à portée de chunk pour allumer la fumée — rendrait le repère inutile.</p>
 *
 * <p>Aucune modification de bloc n'a lieu tant que le chunk n'est pas déjà
 * chargé : le manager ne force jamais le chargement d'un chunk.</p>
 */
public final class PyreManager {

    /** Marqueur de construction unique, persisté dans le monde. */
    private static final String ONE_SHOT_KEY = "signal_pyre_built";

    private static final int MIN_DISTANCE = 1100;
    private static final int MAX_DISTANCE = 2600;
    /** Distance à laquelle le camp se construit réellement. */
    private static final int BUILD_DISTANCE = 96;
    /** Portée de diffusion forcée des particules en 26.1.2. */
    private static final int PLUME_RANGE = 480;

    private static final int PLUME_BOTTOM = 104;
    private static final int PLUME_TOP = 168;
    /** Cadence d'émission du panache (0,5 s) et du test de construction (5 s). */
    private static final int PLUME_INTERVAL_TICKS = 10;
    private static final int BUILD_INTERVAL_TICKS = 100;

    private PyreManager() {
    }

    /**
     * Position (x, z) du bûcher pour cette graine. Purement arithmétique :
     * aucun accès au monde, donc utilisable avant tout chargement de chunk.
     */
    public static BlockPos site(long worldSeed) {
        long mixed = worldSeed * 0x9E3779B97F4A7C15L;
        mixed ^= (mixed >>> 29);
        mixed *= 0xBF58476D1CE4E5B9L;
        mixed ^= (mixed >>> 32);
        double angle = (Math.abs(mixed % 100_000L) / 100_000.0) * Math.PI * 2.0;
        long second = mixed * 0x94D049BB133111EBL;
        second ^= (second >>> 31);
        int distance = MIN_DISTANCE
                + (int) (Math.abs(second % (MAX_DISTANCE - MIN_DISTANCE + 1L)));
        int x = (int) Math.round(Math.cos(angle) * distance);
        int z = (int) Math.round(Math.sin(angle) * distance);
        return new BlockPos(x, 0, z);
    }

    /**
     * Décalages de secours, en blocs, quand le tirage tombe en mer (0.19.0).
     *
     * <p><b>Le défaut que ça corrige.</b> La position venait de la graine et de
     * rien d'autre. Sur un monde où elle tombait dans l'océan — et c'est arrivé
     * au premier essai réel — le code posait le marqueur « inutile de
     * réessayer » et le monde n'avait <b>ni Bûcher, ni Relevé, ni les quinze
     * pages</b>. Une pièce lancée décidait si la clé de tout le mod existait.
     * Inacceptable pour du contenu unique.</p>
     *
     * <p><b>Comment on cherche la terre.</b> Sans générer un seul chunk :
     * {@code ChunkGenerator.getBaseHeight} échantillonne le bruit de terrain à
     * n'importe quelle coordonnée, c'est ce qu'utilise {@code /locate}. On
     * balaie des anneaux de plus en plus larges autour du point d'origine
     * jusqu'à trouver un sol franchement au-dessus de la mer. Le balayage est
     * déterministe : deux joueurs d'une même carte obtiennent le même site.</p>
     */
    private static final int SEARCH_RINGS = 14;
    private static final int SEARCH_STEP = 140;
    private static final int SEARCH_ANGLES = 12;
    /** Marge au-dessus du niveau de la mer : une plage à peine émergée ne vaut rien. */
    private static final int LAND_MARGIN = 3;

    /** Site résolu par monde, calculé une fois : le balayage coûte trop pour un tick. */
    private static final Map<ResourceKey<Level>, BlockPos> RESOLVED = new HashMap<>();

    /**
     * Position réellement utilisée, garantie sur la terre ferme quand c'est
     * possible. Retombe sur le tirage brut si le monde est un océan complet.
     */
    public static BlockPos resolvedSite(ServerLevel level) {
        BlockPos cached = RESOLVED.get(level.dimension());
        if (cached != null) {
            return cached;
        }
        BlockPos base = site(level.getSeed());
        BlockPos found = isLand(level, base.getX(), base.getZ()) ? base : searchLand(level, base);
        RESOLVED.put(level.dimension(), found);
        if (!found.equals(base)) {
            PenumbraMod.LOGGER.info(
                    "[Penumbra] Site du Bûcher déplacé de [{}, {}] vers [{}, {}] : mer au tirage",
                    base.getX(), base.getZ(), found.getX(), found.getZ());
        }
        return found;
    }

    /**
     * Anneaux concentriques, du plus proche au plus lointain : le site retenu
     * est donc toujours le plus proche du tirage d'origine.
     *
     * <p>Le nombre de points <b>croît avec le rayon</b>. À nombre fixe, les
     * essais s'écartent proportionnellement au rayon — douze points sur un
     * anneau de deux mille blocs laissent mille blocs entre deux sondages, de
     * quoi manquer une île entière. Ici l'écart reste d'environ cent blocs sur
     * tous les anneaux.</p>
     */
    private static BlockPos searchLand(ServerLevel level, BlockPos base) {
        for (int ring = 1; ring <= SEARCH_RINGS; ring++) {
            int radius = ring * SEARCH_STEP;
            int angles = SEARCH_ANGLES + ring * 2;
            for (int step = 0; step < angles; step++) {
                // Le décalage d'un demi-cran par anneau évite d'aligner tous les
                // essais sur les mêmes rayons et couvre mieux la surface.
                double angle = (step + (ring % 2) * 0.5) / angles * Math.PI * 2.0;
                int x = base.getX() + (int) Math.round(Math.cos(angle) * radius);
                int z = base.getZ() + (int) Math.round(Math.sin(angle) * radius);
                if (isLand(level, x, z)) {
                    return new BlockPos(x, 0, z);
                }
            }
        }
        return base;
    }

    /** Sol solide franchement au-dessus de la mer, sans générer le chunk. */
    private static boolean isLand(ServerLevel level, int x, int z) {
        ServerChunkCache cache = level.getChunkSource();
        int floor = cache.getGenerator().getBaseHeight(x, z,
                Heightmap.Types.OCEAN_FLOOR_WG, level, cache.randomState());
        return floor > level.getSeaLevel() + LAND_MARGIN;
    }

    /** Oublie le site résolu : arrêt de serveur, ou commande de diagnostic. */
    public static void forget() {
        RESOLVED.clear();
    }

    /** À brancher sur {@code ServerTickEvents.END_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.journalEnabled || !config.signalPyreEnabled) {
            return;
        }
        ServerLevel overworld = server.overworld();
        BlockPos site = resolvedSite(overworld);
        long tick = server.getTickCount();

        if (tick % PLUME_INTERVAL_TICKS == 0) {
            emitPlume(overworld, site);
        }
        if (tick % BUILD_INTERVAL_TICKS == 0) {
            tryBuild(server, overworld, site);
        }
    }

    /**
     * Portée réellement utile pour un joueur, en blocs.
     *
     * <p><b>Corrigé en 0.19.0.</b> La version précédente émettait jusqu'à 480
     * blocs, la limite du mode forcé côté serveur. C'était exact et inutile :
     * le brouillard de Minecraft se ferme à peu près à la distance d'affichage
     * du client, et tout ce qui est au-delà est peint en couleur de ciel. Un
     * joueur en 12 chunks ne voyait donc <b>rien</b> — la fumée était bien
     * envoyée, bien reçue, et parfaitement invisible.</p>
     *
     * <p>On se cale maintenant sur ce que le client peut afficher : 80 % de sa
     * distance de rendu, jamais moins de 96 blocs ni plus de 480. Le repère
     * porte moins loin qu'annoncé, mais il existe.</p>
     */
    private static double visibleRange(ServerPlayer player) {
        int chunks = Math.max(2, player.requestedViewDistance());
        return Math.max(96.0, Math.min(PLUME_RANGE, chunks * 16.0 * 0.8));
    }

    /**
     * Panache haut, diffusé individuellement en mode forcé. Le mode forcé est
     * ce qui porte la particule à 512 blocs au lieu de 32 ; sans lui, aucun
     * repère lointain n'est possible.
     */
    private static void emitPlume(ServerLevel level, BlockPos site) {
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double dx = player.getX() - site.getX();
            double dz = player.getZ() - site.getZ();
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            double range = visibleRange(player);
            if (horizontal > range) {
                continue;
            }
            // Densité : jamais moins de trois particules par pulsation.
            // La version précédente descendait à une seule quand on approchait,
            // en supposant que le feu du camp prendrait le relais — sauf que
            // tant que le camp n'est pas bâti il n'y a aucun feu, et le joueur
            // arrivait donc sur un site parfaitement muet. Une particule isolée
            // répartie sur une colonne de soixante-quatre blocs ne se voit pas.
            double ratio = horizontal / range;
            int count = 3 + (int) Math.round(ratio * 6.0);
            for (int index = 0; index < count; index++) {
                // Répartition régulière sur la hauteur plutôt qu'aléatoire :
                // à distance, un tirage au sort laisse des trous dans la
                // colonne et le panache se lit comme des taches éparses.
                double step = (index + level.getRandom().nextDouble()) / count;
                double y = PLUME_BOTTOM + step * (PLUME_TOP - PLUME_BOTTOM);
                // Le panache s'incline avec l'altitude : une colonne parfaitement
                // droite ne ressemble pas à de la fumée.
                double drift = (y - PLUME_BOTTOM) * 0.22;
                level.sendParticles(player, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                        true, false,
                        site.getX() + 0.5 + drift, y, site.getZ() + 0.5 + drift * 0.4,
                        1, 1.6, 2.4, 1.6, 0.006);
            }
        }
    }

    /** Distance horizontale au bûcher, pour les commandes de diagnostic. */
    public static double distanceTo(ServerPlayer player, BlockPos site) {
        double dx = player.getX() - site.getX();
        double dz = player.getZ() - site.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Portée d'émission effective pour ce joueur, pour les commandes. */
    public static double plumeRangeFor(ServerPlayer player) {
        return visibleRange(player);
    }

    /** Le camp a-t-il déjà été construit dans ce monde ? */
    public static boolean isBuilt(MinecraftServer server) {
        return PenumbraPersistentState.get(server).isOneShotUsed(ONE_SHOT_KEY);
    }

    /**
     * Construit le camp la première fois qu'un joueur s'en approche vraiment,
     * si et seulement si le chunk est déjà chargé. Rien n'est jamais forcé.
     */
    private static void tryBuild(MinecraftServer server, ServerLevel level, BlockPos site) {
        PenumbraPersistentState state = PenumbraPersistentState.get(server);
        if (state.isOneShotUsed(ONE_SHOT_KEY)) {
            return;
        }
        boolean someoneClose = false;
        for (ServerPlayer player : level.players()) {
            double dx = player.getX() - site.getX();
            double dz = player.getZ() - site.getZ();
            if (dx * dx + dz * dz <= (double) BUILD_DISTANCE * BUILD_DISTANCE) {
                someoneClose = true;
                break;
            }
        }
        if (!someoneClose) {
            return;
        }
        build(server, level, site);
    }

    /** Issue d'une tentative de construction, pour les journaux et les commandes. */
    public enum Result {
        BUILT,
        ALREADY_BUILT,
        CHUNK_NOT_LOADED,
        UNDERWATER,
        FAILED
    }

    /**
     * Construit le camp au site du monde. Utilisable par le tick comme par
     * {@code /penumbra pyre build}.
     *
     * <p><b>Corrigé en 0.19.0 :</b> le marqueur « déjà construit » était posé
     * dans un {@code finally}, donc <b>même quand la construction échouait</b>.
     * Un seul raté — une exception, un chunk à moitié prêt — condamnait
     * définitivement le Bûcher de ce monde, sans message et sans recours. Il
     * n'est plus posé qu'en cas de réussite franche, ou de site immergé, qui
     * est le seul échec qu'il soit inutile de réessayer.</p>
     */
    public static Result build(MinecraftServer server, ServerLevel level, BlockPos site) {
        PenumbraPersistentState state = PenumbraPersistentState.get(server);
        if (state.isOneShotUsed(ONE_SHOT_KEY)) {
            return Result.ALREADY_BUILT;
        }
        int chunkX = site.getX() >> 4;
        int chunkZ = site.getZ() >> 4;
        if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
            return Result.CHUNK_NOT_LOADED;
        }
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, site.getX(), site.getZ());
        if (surfaceY <= level.getSeaLevel()) {
            // Seul échec définitif : le tirage est tombé en mer. Inutile de
            // retester, le fond ne remontera pas.
            state.markOneShot(ONE_SHOT_KEY);
            PenumbraMod.LOGGER.info(
                    "[Penumbra] Site du Bûcher immergé en [{}, {}] : aucun camp généré",
                    site.getX(), site.getZ());
            return Result.UNDERWATER;
        }
        BlockPos ground = new BlockPos(site.getX(), surfaceY, site.getZ());
        try {
            BlockPos anchor = SignalPyreBuilder.build(level, ground, level.getSeed());
            if (anchor == null) {
                return Result.FAILED;
            }
            StructureTracker.get(server).record(
                    StructureType.SIGNAL_PYRE, anchor, level, 0);
            state.markOneShot(ONE_SHOT_KEY);
            PenumbraMod.LOGGER.info("[Penumbra] Bûcher construit en [{}, {}, {}]",
                    anchor.getX(), anchor.getY(), anchor.getZ());
            return Result.BUILT;
        } catch (RuntimeException exception) {
            // On ne marque rien : la prochaine passe réessaiera.
            PenumbraMod.LOGGER.error("[Penumbra] Échec de construction du Bûcher", exception);
            return Result.FAILED;
        }
    }

    /** Oublie la construction : la prochaine approche rebâtira le camp. */
    public static void reset(MinecraftServer server) {
        PenumbraPersistentState.get(server).clearOneShot(ONE_SHOT_KEY);
    }

    /**
     * Direction et distance approximatives du bûcher, pour l'indice des jumelles.
     * Retourne {@code null} hors de l'Overworld.
     */
    public static BlockPos siteFor(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        return resolvedSite(level);
    }
}
