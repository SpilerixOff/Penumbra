package net.fabricmc.fabric.api.client.screen.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
public final class ScreenEvents {
    @FunctionalInterface public interface AfterInit { void afterInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight); }
    @FunctionalInterface public interface BeforeInit { void beforeInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight); }
    @FunctionalInterface public interface Remove { void onRemove(Screen screen); }
    public static final Event<AfterInit> AFTER_INIT = null;
    public static final Event<BeforeInit> BEFORE_INIT = null;
    public static Event<Remove> remove(Screen screen) { return null; }
}
