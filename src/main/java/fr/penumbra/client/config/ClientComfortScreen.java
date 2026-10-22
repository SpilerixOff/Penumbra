package fr.penumbra.client.config;

import com.mojang.blaze3d.platform.InputConstants;
import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.function.Consumer;

/** A draft editor: neither presets nor Cancel change live settings. */
public final class ClientComfortScreen extends Screen {
    private static boolean registered;
    private final Screen parent;
    private ClientComfortSettings draft = ClientComfortSettings.get().copy();
    private Component error = Component.empty();
    private String errorDetail;
    private int panelTop;
    private int panelWidth;
    private int panelLeft;

    public ClientComfortScreen(Screen parent) {
        super(text("title"));
        this.parent = parent;
        errorDetail = ClientComfortSettings.loadError();
        if (errorDetail != null) {
            error = text("read_failed");
        }
    }

    /** No server command, item or operator permission is involved. */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        KeyMapping key = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.penumbra.comfort", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K,
                KeyMapping.Category.register(PenumbraMod.id("comfort"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) {
                // Drain clicks even over chat/inventory: they must not open later on closing it.
                if (client.player != null && client.screen == null) {
                    client.setScreen(new ClientComfortScreen(null));
                }
            }
        });
    }

    @Override
    protected void init() {
        panelWidth = Math.min(440, width - 16);
        panelLeft = (width - panelWidth) / 2;
        panelTop = Math.max(0, (height - 158) / 2);
        int presetWidth = (panelWidth - 8) / 3;
        addPreset("immersion", 0, presetWidth);
        addPreset("motion", 1, presetWidth);
        addPreset("gentle", 2, presetWidth);

        int columnWidth = (panelWidth - 8) / 2;
        toggle("motion", 0, 0, columnWidth, draft.reduceMotion, value -> draft.reduceMotion = value);
        toggle("flashes", 1, 0, columnWidth, draft.blockFlashes, value -> draft.blockFlashes = value);
        toggle("grain", 0, 1, columnWidth, draft.grainEnabled, value -> draft.grainEnabled = value);
        toggle("flicker", 1, 1, columnWidth, draft.lightFlickerEnabled, value -> draft.lightFlickerEnabled = value);
        addRenderableWidget(new VolumeSlider(panelLeft, panelTop + 102, columnWidth, "sudden",
                draft.suddenSoundVolume, value -> draft.suddenSoundVolume = value));
        addRenderableWidget(new VolumeSlider(panelLeft + columnWidth + 8, panelTop + 102, columnWidth, "ambient",
                draft.ambientSoundVolume, value -> draft.ambientSoundVolume = value));

        addRenderableWidget(Button.builder(text("apply"), button -> apply())
                .bounds(panelLeft, panelTop + 136, columnWidth, 20).build());
        addRenderableWidget(Button.builder(text("cancel"), button -> onClose())
                .bounds(panelLeft + columnWidth + 8, panelTop + 136, columnWidth, 20).build());
    }

    private void addPreset(String preset, int column, int buttonWidth) {
        addRenderableWidget(Button.builder(text("preset." + preset), button -> {
            draft = new ClientComfortSettings();
            if (!preset.equals("immersion")) {
                draft.reduceMotion = true;
                draft.blockFlashes = true;
                draft.afterimagesEnabled = false;
                draft.lightFlickerEnabled = false;
            }
            if (preset.equals("gentle")) {
                draft.hideFaces = true;
                draft.grainEnabled = false;
                draft.suddenSoundVolume = 0.45f;
                draft.ambientSoundVolume = 0.6f;
            }
            rebuildWidgets();
        }).bounds(panelLeft + column * (buttonWidth + 4), panelTop + 34, buttonWidth, 20)
                .tooltip(Tooltip.create(text("preset." + preset + ".tip"))).build());
    }

    private void toggle(String key, int column, int row, int buttonWidth,
                        boolean current, Consumer<Boolean> update) {
        addRenderableWidget(CycleButton.onOffBuilder(current)
                .withTooltip(value -> Tooltip.create(text(key + ".tip")))
                .create(panelLeft + column * (buttonWidth + 8), panelTop + 58 + row * 22,
                        buttonWidth, 20, text(key), (button, value) -> update.accept(value)));
    }

    private void apply() {
        try {
            ClientComfortSettings.saveAndApply(draft);
            ClientComfort.onSettingsChanged();
            onClose();
        } catch (IOException exception) {
            error = text("write_failed");
            errorDetail = exception.getMessage();
            PenumbraMod.LOGGER.warn("Cannot save local comfort settings", exception);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0101217);
        graphics.centeredText(font, title, width / 2, panelTop + 8, 0xFFF1E8D8);
        graphics.centeredText(font, text("local"), width / 2, panelTop + 21, 0xFFB7B7B7);
        if (!error.getString().isEmpty()) {
            graphics.centeredText(font, error, width / 2, panelTop + 124, 0xFFFFA5A5);
            if (mouseY >= panelTop + 122 && mouseY < panelTop + 136 && errorDetail != null) {
                graphics.setTooltipForNextFrame(error.copy().append("\n").append(errorDetail), mouseX, mouseY);
            }
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true; // Vanilla pauses a solo world only; multiplayer keeps running.
    }

    private static Component text(String key) {
        return Component.translatable("screen.penumbra.comfort." + key);
    }

    private static final class VolumeSlider extends AbstractSliderButton {
        private final String key;
        private final Consumer<Float> update;

        VolumeSlider(int x, int y, int width, String key, float value, Consumer<Float> update) {
            super(x, y, width, 20, Component.empty(), value);
            this.key = key;
            this.update = update;
            setTooltip(Tooltip.create(text(key + ".tip")));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.penumbra.comfort." + key, Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            update.accept((float) value);
        }
    }
}
