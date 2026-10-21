package fr.penumbra.photo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

/** Placement commun du cadre vide et d'une photo directement encadrée. */
final class PhotoFramePlacement {

    private PhotoFramePlacement() {
    }

    static InteractionResult place(UseOnContext context, ItemStack framedPhoto) {
        Direction direction = context.getClickedFace();
        BlockPos pos = context.getClickedPos().relative(direction);
        Player player = context.getPlayer();
        ItemStack source = context.getItemInHand();
        if (direction.getAxis().isVertical()
                || player != null && !player.mayUseItemAt(pos, direction, source)) {
            return InteractionResult.FAIL;
        }

        Level level = context.getLevel();
        PhotoFrameEntity frame = new PhotoFrameEntity(level, pos, direction);
        if (!framedPhoto.isEmpty()) {
            frame.setItem(framedPhoto.copyWithCount(1), false);
        }
        if (!frame.survives()) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide()) {
            frame.playPlacementSound();
            level.gameEvent(player, GameEvent.ENTITY_PLACE, frame.position());
            level.addFreshEntity(frame);
        }
        source.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
