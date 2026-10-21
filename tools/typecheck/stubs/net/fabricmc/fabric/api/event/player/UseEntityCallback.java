package net.fabricmc.fabric.api.event.player;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
@FunctionalInterface public interface UseEntityCallback {
    Event<UseEntityCallback> EVENT = null;
    InteractionResult interact(Player player, Level world, InteractionHand hand, Entity entity, EntityHitResult hitResult);
}
