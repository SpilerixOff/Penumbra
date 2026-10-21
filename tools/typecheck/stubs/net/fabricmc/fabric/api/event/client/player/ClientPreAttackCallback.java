package net.fabricmc.fabric.api.event.client.player;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
@FunctionalInterface public interface ClientPreAttackCallback {
    Event<ClientPreAttackCallback> EVENT = null;
    boolean onClientPlayerPreAttack(Minecraft client, LocalPlayer player, int clickCount);
}
