package net.fabricmc.fabric.api.event.player;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public final class PlayerBlockBreakEvents {
    @FunctionalInterface public interface Before {
        boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity entity);
    }
    @FunctionalInterface public interface After {
        void afterBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity entity);
    }
    @FunctionalInterface public interface Canceled {
        void onBlockBreakCanceled(Level world, Player player, BlockPos pos, BlockState state, BlockEntity entity);
    }
    public static final Event<Before> BEFORE = null;
    public static final Event<After> AFTER = null;
    public static final Event<Canceled> CANCELED = null;
}
