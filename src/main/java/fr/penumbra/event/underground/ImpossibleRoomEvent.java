package fr.penumbra.event.underground;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.registry.PenumbraParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Salle impossible (Phase 3, {@code impossible_room}) — TRÈS RARE (poids ~×0.1).
 * <p>
 * Une petite pièce 3×3×3 est creusée dans la roche PLEINE uniquement (stone, deepslate,
 * tuff, andésite, diorite, granite — jamais gravier/sable/terre, jamais d'eau ni de lave,
 * jamais de bloc à entité : les donjons sont exclus), à 4–10 blocs du joueur derrière une
 * paroi. Un seul bloc d'air d'ouverture relie la pièce à la galerie (28 blocs modifiés au
 * total, ≤ 30). Contenu : un souffle de cendre ({@code ASH_MOTE}), un panneau mural avec
 * une phrase incomplète (clé lang {@code penumbra.sign.room_1..3}) et, à 35 %, un
 * {@code penumbra:observation_fragment} au sol (item entity ; ignoré silencieusement si
 * l'objet n'existe pas dans le registre). Chaque bloc créé est TRACKÉ (permanent,
 * {@link UndergroundRestorationTracker#NO_DEADLINE}) : {@code restoreAll} retire la pièce
 * en ne restaurant que les blocs restés intacts. Exige {@code worldModificationEnabled}.
 * Gain de tension : +10.
 * </p>
 */
public final class ImpossibleRoomEvent extends AbstractUndergroundEvent {

    /** Distance minimale du centre de la salle (blocs). */
    private static final int MIN_DISTANCE = 4;
    /** Distance maximale du centre de la salle (blocs). */
    private static final int MAX_DISTANCE = 10;
    /** Essais de placement (direction × distance × dénivelé). */
    private static final int ATTEMPTS = 16;
    /** Probabilité de dépôt d'un fragment d'observation. */
    private static final float FRAGMENT_CHANCE = 0.35f;
    /** Nombre de phrases de panneau disponibles (clés penumbra.sign.room_1..N). */
    private static final int SIGN_VARIANTS = 3;

    @Override
    public String id() {
        return "impossible_room";
    }

    @Override
    public int weight(TensionState state) {
        // Poids entiers : 1 ≈ ×0.1 d'un événement courant (≈10). Jamais en CALM/HUNT/CRISIS.
        return switch (state) {
            case CALM -> 0;
            case UNEASE -> 1;
            case WATCHED -> 1;
            case MANIFESTATION -> 1;
            case HUNT -> 0;
            case CRISIS -> 0;
            case FALSE_CALM -> 1;
        };
    }

    @Override
    public int cooldownTicks() {
        return 24000;
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!PenumbraConfig.get().worldModificationEnabled) {
            return false;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        UndergroundRestorationTracker.checkDeadlines(level); // maintenance amortie
        RandomSource random = player.getRandom();

        Direction[] directions = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Direction fromPlayer = directions[random.nextInt(directions.length)];
            int distance = MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1);
            int dy = random.nextInt(3) - 1; // -1..+1
            BlockPos center = player.blockPosition()
                    .relative(fromPlayer, distance).above(dy);
            if (tryPlaceRoom(level, player, center, fromPlayer, random)) {
                return true;
            }
        }
        // Aucun emplacement valide : événement annulé sans effet.
        return false;
    }

    /**
     * Tente de creuser la salle centrée sur {@code center}, l'ouverture faisant face au
     * joueur (direction {@code fromPlayer} inversée). Retourne vrai si la salle a été créée.
     * Toutes les vérifications précèdent la moindre modification du monde.
     */
    private static boolean tryPlaceRoom(ServerLevel level, ServerPlayer player, BlockPos center,
                                        Direction fromPlayer, RandomSource random) {
        // Bornes verticales : le cube 3×3×3 entier doit rester dans le monde.
        if (center.getY() - 1 < level.getMinY() + 1 || center.getY() + 1 > level.getMaxY() - 1) {
            return false;
        }
        // Les 27 blocs du cube doivent être de la roche pleine (ni fluide, ni entité de
        // bloc, ni gravier/sable/terre — voir isHardRock).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!isHardRock(level.getBlockState(center.offset(dx, dy, dz)))) {
                        return false;
                    }
                }
            }
        }
        // Ouverture : le bloc de paroi entre la salle et la galerie doit être de la roche
        // pleine, et le bloc au-delà (côté joueur) doit être de l'air (la galerie).
        Direction toPlayer = fromPlayer.getOpposite();
        BlockPos opening = center.relative(toPlayer, 2);
        BlockPos gallery = center.relative(toPlayer, 3);
        if (!isHardRock(level.getBlockState(opening)) || !level.getBlockState(gallery).isAir()) {
            return false;
        }

        UndergroundRestorationTracker tracker = UndergroundRestorationTracker.get(level.getServer());
        String uuid = player.getUUID().toString();
        BlockState air = Blocks.AIR.defaultBlockState();

        // Creusage : 27 blocs d'air + 1 bloc d'ouverture = 28 blocs modifiés (≤ 30).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState original = level.getBlockState(pos);
                    level.setBlock(pos, air, 3);
                    tracker.add(level, new UndergroundRestorationTracker.Entry(
                            pos.immutable(), level.dimension(), original, air,
                            UndergroundRestorationTracker.TYPE_ROOM,
                            UndergroundRestorationTracker.NO_DEADLINE, uuid));
                }
            }
        }
        BlockState openingOriginal = level.getBlockState(opening);
        level.setBlock(opening, air, 3);
        tracker.add(level, new UndergroundRestorationTracker.Entry(
                opening.immutable(), level.dimension(), openingOriginal, air,
                UndergroundRestorationTracker.TYPE_ROOM,
                UndergroundRestorationTracker.NO_DEADLINE, uuid));

        // Panneau sur la paroi du fond, face à l'ouverture (phrase incomplète).
        placeSign(level, tracker, center, fromPlayer, uuid, random);

        // Cendre flottante dans la salle.
        Vec3 middle = Vec3.atCenterOf(center);
        level.sendParticles(PenumbraParticles.ASH_MOTE, middle.x, middle.y, middle.z,
                20, 1.2, 1.0, 1.2, 0.01);

        // Fragment d'observation au sol (35 %) — ignoré si l'objet n'existe pas encore.
        if (random.nextFloat() < FRAGMENT_CHANCE) {
            Item fragment = BuiltInRegistries.ITEM.getValue(
                    Identifier.fromNamespaceAndPath("penumbra", "observation_fragment"));
            if (fragment != null) {
                ItemEntity drop = new ItemEntity(level,
                        center.getX() + 0.5, center.getY() - 0.5, center.getZ() + 0.5,
                        new ItemStack(fragment));
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
        return true;
    }

    /**
     * Pose le panneau mural sur la paroi du fond de la salle (côté opposé à l'ouverture),
     * orienté vers l'entrée, avec une phrase incomplète en deuxième ligne
     * ({@code penumbra.sign.room_1..3}). Le bloc remplacé est l'air fraîchement creusé ;
     * il est TRACKÉ comme le reste de la salle.
     */
    private static void placeSign(ServerLevel level, UndergroundRestorationTracker tracker,
                                  BlockPos center, Direction fromPlayer, String uuid,
                                  RandomSource random) {
        BlockPos signPos = center.relative(fromPlayer, 1); // face interne de la paroi du fond
        BlockState original = level.getBlockState(signPos); // air creusé à l'instant
        BlockState signState = Blocks.OAK_WALL_SIGN.defaultBlockState()
                .setValue(WallSignBlock.FACING, fromPlayer.getOpposite());
        if (!signState.canSurvive(level, signPos)) {
            return; // paroi porteuse invalide : la salle reste sans panneau
        }
        level.setBlock(signPos, signState, 3);
        tracker.add(level, new UndergroundRestorationTracker.Entry(
                signPos.immutable(), level.dimension(), original, signState,
                UndergroundRestorationTracker.TYPE_ROOM,
                UndergroundRestorationTracker.NO_DEADLINE, uuid));

        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            int variant = 1 + random.nextInt(SIGN_VARIANTS);
            Component line = Component.translatable("penumbra.sign.room_" + variant);
            Component[] front = {Component.empty(), line, Component.empty(), Component.empty()};
            Component[] back = {Component.empty(), Component.empty(), Component.empty(), Component.empty()};
            sign.setText(new SignText(front, back, DyeColor.BLACK, false), true);
            sign.setChanged();
            level.sendBlockUpdated(signPos, signState, signState, 3);
        }
    }
}
