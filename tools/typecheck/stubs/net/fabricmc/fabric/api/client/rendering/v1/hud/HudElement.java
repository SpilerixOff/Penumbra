package net.fabricmc.fabric.api.client.rendering.v1.hud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
@FunctionalInterface public interface HudElement {
    void render(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker);
}
