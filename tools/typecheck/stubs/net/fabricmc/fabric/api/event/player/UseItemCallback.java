package net.fabricmc.fabric.api.event.player;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
@FunctionalInterface public interface UseItemCallback {
    Event<UseItemCallback> EVENT = null;
    InteractionResult interact(Player player, Level world, InteractionHand hand);
}
