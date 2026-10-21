package fr.penumbra.photo;

import fr.penumbra.registry.PenumbraEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Petit cadre mural : support/drop/synchronisation hérités du cadre vanilla. */
public final class PhotoFrameEntity extends ItemFrame {

    private static final double WIDTH = 0.68;
    private static final double HEIGHT = 0.56;
    private static final double DEPTH = 0.0625;

    public PhotoFrameEntity(EntityType<? extends PhotoFrameEntity> type, Level level) {
        super(type, level);
    }

    public PhotoFrameEntity(Level level, BlockPos pos, Direction direction) {
        super(PenumbraEntities.PHOTO_FRAME, level, pos, direction);
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        return smallBox(pos, direction);
    }

    @Override
    protected AABB getPopBox() {
        return smallBox(this.pos, getDirection());
    }

    private static AABB smallBox(BlockPos pos, Direction direction) {
        Vec3 center = Vec3.atCenterOf(pos).relative(direction, -0.46875);
        Direction.Axis axis = direction.getAxis();
        double x = axis == Direction.Axis.X ? DEPTH : WIDTH;
        double z = axis == Direction.Axis.Z ? DEPTH : WIDTH;
        return AABB.ofSize(center, x, HEIGHT, z);
    }

    @Override
    protected ItemStack getFrameItemStack() {
        return new ItemStack(PhotoContent.PHOTO_FRAME);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hit) {
        if (getItem().isEmpty() && !PhotoContent.isPhoto(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        return super.interact(player, hand, hit);
    }
}
