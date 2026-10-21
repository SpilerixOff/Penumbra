package fr.penumbra.entity.originel;

import fr.penumbra.registry.PenumbraItems;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Rituel multibloc de l'Originel. Le joueur assemble le totem puis touche la
 * citrouille sculptée avec le Sceau de cendre : aucune commande opérateur n'est
 * nécessaire et le sceau n'est pas consommé.
 */
public final class HauntedTotem {

    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private HauntedTotem() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(HauntedTotem::interact);
    }

    private static InteractionResult interact(net.minecraft.world.entity.player.Player player,
                                              Level level,
                                              net.minecraft.world.InteractionHand hand,
                                              BlockHitResult hit) {
        BlockPos top = hit.getBlockPos();
        if (!player.getItemInHand(hand).is(PenumbraItems.ASH_SIGIL)
                || !matches(level, top)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        boolean summoned = OriginelManager.forceTotemSighting(serverPlayer, top);
        serverPlayer.sendSystemMessage(Component.translatable(summoned
                ? "message.penumbra.haunted_totem_awakes"
                : "message.penumbra.haunted_totem_resists"));
        if (summoned) {
            serverLevel.sendParticles(ParticleTypes.SOUL,
                    top.getX() + 0.5, top.getY() - 0.25, top.getZ() + 0.5,
                    42, 1.25, 1.8, 1.25, 0.025);
            serverLevel.sendParticles(ParticleTypes.SMOKE,
                    top.getX() + 0.5, top.getY() + 0.25, top.getZ() + 0.5,
                    28, 0.7, 1.1, 0.7, 0.01);
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * De bas en haut : bloc d'or, sable des âmes, citrouille sculptée. Autour
     * du bloc d'or : quatre pierres noires polies, chacune portant une torche
     * de redstone au niveau du sable des âmes.
     */
    static boolean matches(Level level, BlockPos top) {
        if (!level.getBlockState(top).is(Blocks.CARVED_PUMPKIN)
                || !level.getBlockState(top.below()).is(Blocks.SOUL_SAND)) {
            return false;
        }
        BlockPos base = top.below(2);
        if (!level.getBlockState(base).is(Blocks.GOLD_BLOCK)) {
            return false;
        }
        for (Direction direction : HORIZONTAL) {
            BlockPos support = base.relative(direction);
            if (!level.getBlockState(support).is(Blocks.POLISHED_BLACKSTONE)
                    || !level.getBlockState(support.above()).is(Blocks.REDSTONE_TORCH)) {
                return false;
            }
        }
        return true;
    }
}
