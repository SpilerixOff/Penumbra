package fr.penumbra.content.item;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Marteau minier 4x4. Le bloc visé est cassé normalement par Minecraft, puis
 * les quinze autres blocs du plan sont confiés au mode de jeu serveur. Cette
 * voie conserve les protections, les enchantements, les butins et l'XP vanilla.
 */
public final class HammerItem extends Item {

    private static final int MIN_OFFSET = -1;
    private static final int MAX_OFFSET = 2;

    /**
     * {@link ServerPlayerGameMode#destroyBlock} rappelle {@link #mineBlock}.
     * Ce garde-fou évite qu'un bloc secondaire lance un nouveau carré 4x4.
     */
    private static final Set<UUID> ACTIVE_MINERS = new HashSet<>();

    public HammerItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos origin,
            LivingEntity miner) {
        boolean mined = super.mineBlock(stack, level, state, origin, miner);
        if (!(level instanceof ServerLevel serverLevel)
                || !(miner instanceof ServerPlayer player)
                || ACTIVE_MINERS.contains(player.getUUID())
                || !canMineWithHammer(stack, serverLevel, origin, state)) {
            return mined;
        }

        /*
         * Le super appel a déjà appliqué exactement un point de durabilité au
         * bloc initial. Chaque destroyBlock secondaire rappellera mineBlock une
         * fois et appliquera donc exactement le même coût au bloc effectivement
         * cassé, y compris les enchantements vanilla comme Unbreaking.
         */
        if (stack.isEmpty() || stack.isBroken() || player.getMainHandItem() != stack) {
            return mined;
        }

        Direction lookDirection = Direction.getApproximateNearest(player.getLookAngle());
        ACTIVE_MINERS.add(player.getUUID());
        try {
            for (int first = MIN_OFFSET; first <= MAX_OFFSET; first++) {
                for (int second = MIN_OFFSET; second <= MAX_OFFSET; second++) {
                    BlockPos target = HammerMiningPlane.offset(
                            origin, lookDirection, first, second);
                    if (target.equals(origin)) {
                        continue;
                    }
                    if (stack.isEmpty() || stack.isBroken() || player.getMainHandItem() != stack) {
                        return mined;
                    }
                    if (!serverLevel.isInWorldBounds(target)
                            || !serverLevel.isLoaded(target)
                            || !player.mayInteract(serverLevel, target)) {
                        continue;
                    }

                    BlockState targetState = serverLevel.getBlockState(target);
                    if (!canMineWithHammer(stack, serverLevel, target, targetState)) {
                        continue;
                    }

                    // Gestion vanilla : événements de casse, drops, Silk Touch,
                    // Fortune, XP, statistiques et coût de durabilité.
                    player.gameMode.destroyBlock(target);
                }
            }
        } finally {
            ACTIVE_MINERS.remove(player.getUUID());
        }
        return mined;
    }

    private static boolean canMineWithHammer(ItemStack stack, ServerLevel level, BlockPos pos,
            BlockState state) {
        return !state.isAir()
                && state.getDestroySpeed(level, pos) >= 0.0F
                && state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && stack.isCorrectToolForDrops(state);
    }

}
