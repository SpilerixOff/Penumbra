package fr.penumbra.prop;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les objets trouvés (0.19.0) — le mobilier qui n'a rien à faire là.
 *
 * <h2>Le principe</h2>
 * <p>Aucun de ces objets n'est effrayant. Une chaise de cuisine est une chaise
 * de cuisine. Ce qui travaille, c'est <b>l'endroit</b> : la même chaise, seule,
 * dos tourné, à quatre cents blocs de toute habitation, sur un chemin que le
 * joueur emprunte tous les jours. Le mod ne fabrique donc pas d'horreur — il
 * pose un objet ordinaire au mauvais endroit et laisse le joueur faire le
 * reste.</p>
 *
 * <h2>Trois règles de pose</h2>
 * <ol>
 *   <li><b>Jamais dans le champ de vision.</b> Un objet qui apparaît est un
 *       effet ; un objet qu'on découvre est un fait. La pose se fait derrière
 *       le joueur, hors de son cône de regard et sans ligne de vue.</li>
 *   <li><b>Ni trop près, ni trop loin.</b> Assez loin pour ne pas être vu
 *       apparaître, assez près pour être croisé — pas un objet abandonné à
 *       trois cents blocs que personne ne verra jamais.</li>
 *   <li><b>Un seul à la fois dans le secteur.</b> Deux chaises font une
 *       décharge ; une chaise fait une question.</li>
 * </ol>
 *
 * <p>Les objets sont de vraies entités de décor sauvegardées avec le monde :
 * ils restent après déconnexion, et le joueur peut repasser devant. Aucun chunk
 * n'est jamais chargé de force.</p>
 */
public final class FoundObjects {

    /** Un objet posable : maillage, hauteur en blocs, orientation, rareté. */
    private record Placement(String mesh, float scale, boolean facePlayer, int weight) {
    }

    /**
     * Le catalogue. Les poids favorisent le mobilier d'intérieur : c'est lui
     * qui produit le décalage, une souche ou un cairn passant plus facilement
     * pour un élément de terrain.
     */
    private static final List<Placement> CATALOGUE = List.of(
            new Placement("chaise", 1.0f, false, 20),   // dos tourné : quelqu'un regardait quelque chose
            new Placement("porte", 1.0f, true, 16),     // un chambranle seul, fermé
            new Placement("miroir", 1.0f, true, 12),
            new Placement("poupee", 1.0f, true, 10),
            new Placement("effigie", 1.0f, true, 10),
            new Placement("croix", 1.0f, true, 9),
            new Placement("cairn", 1.0f, false, 8),
            new Placement("nid", 1.0f, false, 7),
            new Placement("borne", 1.0f, false, 5),
            new Placement("souche", 1.0f, false, 3));

    private static final int TOTAL_WEIGHT =
            CATALOGUE.stream().mapToInt(Placement::weight).sum();

    /** Fréquence de test : une fois toutes les dix secondes suffit largement. */
    private static final int CHECK_INTERVAL_TICKS = 200;
    private static final int CHECK_OFFSET_TICKS = 40;
    /** Une chance sur autant, à chaque test éligible. */
    private static final int BASE_CHANCE_ONE_IN = 9;
    /** Repos de référence entre deux poses, à fréquence normale : quinze minutes. */
    private static final long BASE_COOLDOWN_TICKS = 20L * 60L * 15L;

    private static final double MIN_DISTANCE = 17.0;
    private static final double MAX_DISTANCE = 33.0;
    /** Rayon d'exclusion autour d'un décor existant. Voir règle 3. */
    private static final double SPACING = 56.0;
    /** Écart minimal, en degrés, entre le regard du joueur et la pose. */
    private static final double BEHIND_ANGLE = 78.0;
    private static final int PLACE_ATTEMPTS = 26;
    private static final int MIN_CONTENT_STAGE = 1;

    private static final Map<UUID, Long> LAST_PLACED = new HashMap<>();

    private FoundObjects() {
    }

    /** À brancher sur {@code ServerTickEvents.END_SERVER_TICK}. */
    public static void onServerTick(MinecraftServer server) {
        if (Math.floorMod(server.getTickCount() - CHECK_OFFSET_TICKS,
                CHECK_INTERVAL_TICKS) != 0) {
            return;
        }
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.foundObjectsEnabled || config.eventFrequency <= 0.0f) {
            return;
        }
        long now = server.getTickCount();
        long cooldown = cooldownTicks(config);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!eligible(player, now, cooldown)) {
                continue;
            }
            if (player.getRandom().nextInt(chanceDenominator(config)) != 0) {
                continue;
            }
            if (place(player)) {
                LAST_PLACED.put(player.getUUID(), now);
            }
        }
    }

    /**
     * Fréquence modulée par le réglage global, comme le reste du mod : à 200 %
     * la chance double, à 50 % elle est divisée par deux. Bornée pour qu'un
     * réglage extrême ne transforme pas la carte en brocante.
     */
    private static int chanceDenominator(PenumbraConfig config) {
        double scaled = BASE_CHANCE_ONE_IN / Math.max(0.05, config.eventFrequency);
        return Mth.clamp((int) Math.round(scaled), 2, 240);
    }

    /**
     * Repos entre deux poses, mis à l'échelle du réglage de fréquence.
     *
     * <p>Sans ça, le curseur ne servirait à rien : le repos de quinze minutes
     * écrase complètement le tirage un-sur-neuf, et passer de 50 % à 200 % ne
     * changeait le rythme observé que de dix-huit à seize minutes. C'est le
     * repos qu'il faut faire varier, pas la chance.</p>
     */
    private static long cooldownTicks(PenumbraConfig config) {
        double scaled = BASE_COOLDOWN_TICKS / Math.max(0.05, config.eventFrequency);
        return (long) Mth.clamp(scaled, 20L * 60L * 4L, 20L * 60L * 90L);
    }

    private static boolean eligible(ServerPlayer player, long now, long cooldown) {
        if (!player.isAlive() || player.isSpectator() || player.isSleeping()
                || !(player.level() instanceof ServerLevel level)
                || level.getDifficulty() == Difficulty.PEACEFUL
                || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        if (!HorrorDirector.isGraceComplete(player)
                || HorrorDirector.effectiveContentStage(player) < MIN_CONTENT_STAGE) {
            return false;
        }
        Long last = LAST_PLACED.get(player.getUUID());
        return last == null || now - last >= cooldown;
    }

    /** Tire un objet du catalogue selon les poids. */
    private static Placement pick(RandomSource random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (Placement candidate : CATALOGUE) {
            roll -= candidate.weight();
            if (roll < 0) {
                return candidate;
            }
        }
        return CATALOGUE.get(0);
    }

    private static boolean place(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = player.getRandom();

        // Règle 3 : un secteur qui contient déjà un décor est laissé tranquille.
        if (!level.getEntities(PropEntities.MESH_PROP,
                new AABB(player.blockPosition()).inflate(SPACING),
                existing -> true).isEmpty()) {
            return false;
        }

        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        double lookAngle = look.lengthSqr() < 1.0E-5
                ? Math.toRadians(player.getYRot() + 90.0)
                : Math.atan2(look.z, look.x);

        Placement placement = pick(random);
        for (int attempt = 0; attempt < PLACE_ATTEMPTS; attempt++) {
            // Règle 1 : dans le dos, jamais dans le cône de regard.
            double side = random.nextBoolean() ? 1.0 : -1.0;
            double spread = Math.toRadians(BEHIND_ANGLE
                    + random.nextDouble() * (180.0 - BEHIND_ANGLE));
            double angle = lookAngle + spread * side;
            double distance = MIN_DISTANCE
                    + random.nextDouble() * (MAX_DISTANCE - MIN_DISTANCE);

            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            BlockPos ground = standingSpot(level, x, player.blockPosition().getY(), z);
            if (ground == null || visibleFrom(player, level, ground)) {
                continue;
            }

            float yaw = (float) Math.toDegrees(Math.atan2(
                    player.getZ() - (ground.getZ() + 0.5),
                    player.getX() - (ground.getX() + 0.5))) - 90.0f;
            if (!placement.facePlayer()) {
                yaw += 180.0f;
            }
            // Un objet posé par quelqu'un n'est jamais parfaitement aligné.
            yaw += (random.nextFloat() - 0.5f) * 24.0f;

            Entity prop = MeshPropEntity.place(level,
                    Vec3.atBottomCenterOf(ground), yaw,
                    placement.mesh(), "", placement.scale());
            if (prop == null) {
                continue;
            }
            if (PenumbraConfig.get().debugLogging) {
                PenumbraMod.LOGGER.info("[Penumbra] Objet trouvé « {} » posé en {} pour {}",
                        placement.mesh(), ground.toShortString(),
                        player.getGameProfile().name());
            }
            return true;
        }
        return false;
    }

    /**
     * Premier sol franc sous {@code baseY}, avec la place de tenir debout.
     * N'accède qu'à des chunks déjà chargés : {@code hasChunkAt} évite le
     * chargement forcé qu'un simple {@code getBlockState} déclencherait.
     */
    private static BlockPos standingSpot(ServerLevel level, int x, int baseY, int z) {
        if (!level.hasChunkAt(new BlockPos(x, baseY, z))) {
            return null;
        }
        for (int y = baseY + 4; y >= baseY - 8; y--) {
            BlockPos floor = new BlockPos(x, y - 1, z);
            BlockState below = level.getBlockState(floor);
            if (!below.isSolidRender() || !level.getFluidState(floor).isEmpty()) {
                continue;
            }
            BlockPos feet = new BlockPos(x, y, z);
            if (!level.getBlockState(feet).isAir()
                    || !level.getBlockState(feet.above()).isAir()
                    || !level.getBlockState(feet.above(2)).isAir()
                    || !level.getFluidState(feet).isEmpty()) {
                continue;
            }
            return feet;
        }
        return null;
    }

    /** Vrai si le joueur verrait l'objet apparaître — auquel cas on renonce. */
    private static boolean visibleFrom(ServerPlayer player, ServerLevel level, BlockPos spot) {
        Vec3 target = Vec3.atBottomCenterOf(spot).add(0.0, 1.0, 0.0);
        Vec3 eyes = player.getEyePosition();
        return level.clip(new ClipContext(eyes, target,
                ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player))
                .getType() == HitResult.Type.MISS;
    }

    public static void onPlayerDisconnect(UUID playerId) {
        LAST_PLACED.remove(playerId);
    }

    public static void onServerStopped(MinecraftServer server) {
        LAST_PLACED.clear();
    }
}
