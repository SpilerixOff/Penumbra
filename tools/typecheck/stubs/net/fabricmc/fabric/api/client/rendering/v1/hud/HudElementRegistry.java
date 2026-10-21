package net.fabricmc.fabric.api.client.rendering.v1.hud;
import net.minecraft.resources.Identifier;
public interface HudElementRegistry {
    static void addFirst(Identifier id, HudElement element) { }
    static void addLast(Identifier id, HudElement element) { }
    static void attachElementBefore(Identifier before, Identifier id, HudElement element) { }
    static void attachElementAfter(Identifier after, Identifier id, HudElement element) { }
}
