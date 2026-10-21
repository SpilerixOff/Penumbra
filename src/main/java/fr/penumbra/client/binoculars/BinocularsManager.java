package fr.penumbra.client.binoculars;

import com.mojang.blaze3d.platform.InputConstants;

import org.lwjgl.glfw.GLFW;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.client.config.ClientComfort;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.util.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Gestionnaire client des jumelles (SPEC2 §8 — réécriture 26.1.2).
 * <p>
 * Le zoom FOV n'utilise plus JAMAIS {@code options.fov()} : en 26.1.2 le FOV
 * vit dans {@code net.minecraft.client.Camera} (champs {@code fov}/{@code hudFov}
 * recalculés à chaque frame dans {@code update(DeltaTracker)}), et l'ancienne
 * approche par options n'avait aucun effet visible en jeu. Le facteur de zoom
 * est exposé ici via {@link #getZoomFactor()} et appliqué frame par frame par
 * {@code CameraFovMixin} (division du champ calculé, jamais persistée →
 * restauration exacte et automatique, aucune option joueur modifiée, jamais
 * {@code options.save()}). Aucun hook FOV n'existe dans fabric-api 0.155.2 :
 * le mixin Camera est la voie officiellement retenue par le chef de projet.
 * </p>
 * <p>
 * Machine à états IDLE → ZOOMING_IN → ZOOMED → ZOOMING_OUT → IDLE pilotée par
 * {@link Util#getMillis()} (temps réel : transition fluide à tout FPS, durée
 * indépendante du tickrate). La respiration est purement visuelle :
 * micro-oscillation du zoom intégrée dans {@link #getZoomFactor()} et dérive
 * d'overlay exposée via {@link #getBreathOffsetX()}/{@link #getBreathOffsetY()}
 * — les rotations du joueur ({@code setYRot}/{@code setXRot}) ne sont plus
 * JAMAIS modifiées. Tout retombe à 0 quand la progression retombe à 0.
 * </p>
 * <p>
 * Overlay : deux éléments HUD distincts. {@code binoculars_mask} est enregistré
 * via {@code HudElementRegistry.addFirst} (vérifié par javap dans
 * fabric-rendering-v1 25.3.1) : rendu AVANT le HUD vanilla, qui reste donc
 * lisible par-dessus. {@code binoculars_mask_top} via {@code addLast} ne
 * dessine que si {@code binocularsHideHud} est activé (recouvre alors
 * volontairement le HUD). Le dessin lui-même est délégué à
 * {@code BinocularsOverlayRenderer} (agent parallèle).
 * </p>
 */
@Environment(EnvType.CLIENT)
public final class BinocularsManager {

    /** Amplitude de la respiration sur le facteur de zoom : ±0,15 % × strength × progress. */
    private static final double BREATH_ZOOM_AMPLITUDE = 0.0015;
    /** Période de l'oscillation de respiration (zoom et dérive verticale), en ms (≈ 7 s). */
    private static final double BREATH_PERIOD_MS = 7000.0;
    /** Amplitude de la dérive verticale de l'overlay, en px GUI × strength × progress. */
    private static final float BREATH_OFFSET_Y_PX = 1.5f;
    /** Amplitude de la dérive horizontale de l'overlay, en px GUI × strength × progress. */
    private static final float BREATH_OFFSET_X_PX = 0.4f;
    /** Période de la dérive horizontale, désynchronisée pour un mouvement organique (ms). */
    private static final double BREATH_OFFSET_X_PERIOD_MS = 5300.0;
    /** Pas de zoom appliqué par cran de molette. */
    private static final double SCROLL_STEP = 0.5;

    /**
     * Catégorie de touches Penumbra. Vérifié en 26.1.2 : {@code KeyMapping.Category}
     * est un record, créée via {@code Category.register(Identifier)} ; la clé de
     * traduction générée est {@code key.category.penumbra.penumbra}.
     */
    private static final KeyMapping.Category PENUMBRA_CATEGORY =
            KeyMapping.Category.register(PenumbraMod.id("penumbra"));

    /**
     * Touche de zoom : {@code key.penumbra.binoculars}, défaut C (GLFW 67).
     * Enregistrée dans {@code PenumbraClient} via
     * {@code KeyMappingHelper.registerKeyMapping} (API officielle Fabric 26.1,
     * nom vérifié dans fabric-key-mapping-api-v1 2.0.4).
     */
    public static final KeyMapping ZOOM_KEY = new KeyMapping(
            "key.penumbra.binoculars", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_C, PENUMBRA_CATEGORY);

    /** États de la machine de transition du zoom (pilotée au temps réel). */
    private enum ZoomState {
        IDLE, ZOOMING_IN, ZOOMED, ZOOMING_OUT
    }

    private static ZoomState state = ZoomState.IDLE;
    /** Timestamp {@link Util#getMillis()} du début de la transition courante. */
    private static long stateStartMillis;
    /** Progression brute (0..1) au début de la transition courante (renversement doux). */
    private static float progressAtStateStart;
    /** Zoom effectif de la session (molette), réinitialisé à chaque activation. */
    private static double sessionZoom = 4.0;
    /** Début de l'enregistrement VHS courant, pour le timecode de l'overlay. */
    private static long recordingStartMillis;
    private static boolean toggled;
    private static int debugTicks;
    private static boolean inventoryOpen;

    static {
        // (a) Masque SOUS le HUD vanilla : addFirst → rendu AVANT les éléments
        // vanilla, le HUD reste donc lisible par-dessus le masque des jumelles.
        HudElementRegistry.addFirst(PenumbraMod.id("binoculars_mask"), (extractor, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            PenumbraConfig config = PenumbraConfig.get();
            if (config.binocularsHideHud || client.options.hideGui
                    || !config.binocularsOverlayEnabled || !isZoomActive()) {
                return;
            }
            BinocularsOverlayRenderer.render(extractor,
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight(),
                    getProgress(), Util.getMillis(), config);
        });

        // (b) Masque AU-DESSUS du HUD : même appel via addLast, ne dessine QUE si
        // binocularsHideHud est activé → recouvre alors volontairement le HUD.
        HudElementRegistry.addLast(PenumbraMod.id("binoculars_mask_top"), (extractor, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            PenumbraConfig config = PenumbraConfig.get();
            if (!config.binocularsHideHud || client.options.hideGui
                    || !config.binocularsOverlayEnabled || !isZoomActive()) {
                return;
            }
            BinocularsOverlayRenderer.render(extractor,
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight(),
                    getProgress(), Util.getMillis(), config);
        });

        // Suivi d'ouverture/fermeture des écrans d'inventaire → InventoryStateC2S.
        // API vérifiée : ScreenEvents.AFTER_INIT + ScreenEvents.remove(screen).
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof AbstractContainerScreen<?>) {
                setInventoryOpen(true);
            }
            ScreenEvents.remove(screen).register(removed -> {
                if (removed instanceof AbstractContainerScreen<?>) {
                    setInventoryOpen(false);
                }
            });
        });

        // Déconnexion : purge sur le thread client. Aucun FOV à restaurer :
        // le mixin Camera ne persiste jamais rien.
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> client.execute(() -> resetAll(client)));
    }

    private BinocularsManager() {
    }

    /**
     * Tick client (enregistré via {@code ClientTickEvents.END_CLIENT_TICK}).
     * Gère les modes maintenir/basculer, les conditions d'activation et les
     * transitions d'état. Le FOV lui-même est appliqué par {@code CameraFovMixin}
     * à partir de {@link #getZoomFactor()} ; rien n'est écrit dans les options.
     */
    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.level == null) {
            // Respawn / changement de monde ou de dimension : interruption immédiate.
            resetAll(client);
            return;
        }
        PenumbraConfig config = PenumbraConfig.get();

        if (config.binocularsToggleMode) {
            while (ZOOM_KEY.consumeClick()) {
                toggled = !toggled;
            }
        } else {
            toggled = false;
        }

        boolean debugActive = debugTicks > 0;
        if (debugActive) {
            debugTicks--;
        }

        boolean keyActive = config.binocularsToggleMode ? toggled : ZOOM_KEY.isDown();
        boolean screenOpen = client.screen != null;
        boolean firstPerson = client.options.getCameraType().isFirstPerson();
        boolean hasItem = !config.binocularsRequireItem || hasBinoculars(player);

        boolean wantZoom = config.binocularsEnabled
                && player.isAlive()
                && !screenOpen
                && firstPerson
                && (debugActive || (keyActive && hasItem));

        if (wantZoom) {
            if (state == ZoomState.IDLE) {
                startZoom(player, config);
            } else if (state == ZoomState.ZOOMING_OUT) {
                // Réactivation en pleine sortie : on repart vers l'avant depuis la
                // progression courante, sans rejouer le son ni renvoyer l'état.
                beginTransition(ZoomState.ZOOMING_IN, rawProgress(Util.getMillis(), config));
            }
        } else if (state == ZoomState.ZOOMING_IN || state == ZoomState.ZOOMED) {
            // Interruption immédiate sur écran/mort/3e personne, sortie douce sinon.
            boolean immediate = screenOpen || !firstPerson || !player.isAlive();
            stopZoom(player, config, immediate);
            if (config.binocularsToggleMode && (screenOpen || !player.isAlive())) {
                toggled = false; // pas de reprise surprise à la fermeture de l'écran
            }
        }

        advanceState(config);
    }

    /**
     * Force le zoom côté client pendant {@code ticks} ticks
     * (consommé par {@code /penumbra binoculars test}, Agent_CommandsConfig).
     */
    public static void debugZoom(int ticks) {
        debugTicks = Math.max(debugTicks, Math.max(0, ticks));
    }

    /**
     * Progression EASED (smoothstep) du zoom, de 0 (aucun) à 1 (plein),
     * calculée depuis les timestamps temps réel de la machine à états.
     */
    public static float getProgress() {
        float raw = rawProgress(Util.getMillis(), PenumbraConfig.get());
        return raw * raw * (3.0f - 2.0f * raw);
    }

    /**
     * Facteur de zoom courant : {@code 1 + (sessionZoom - 1) * progress}
     * (≈ ×4 à progression pleine avec les défauts), auquel s'ajoute la
     * micro-oscillation de respiration (±0,15 % × strength, sinus temps réel
     * ≈ 7 s, atténuée par progress) quand {@code binocularsBreathingMotion}
     * est activé. Vaut EXACTEMENT {@code 1.0f} hors zoom → FOV vanilla inchangé.
     */
    public static float getZoomFactor() {
        float progress = getProgress();
        if (progress <= 0.0f) {
            return 1.0f;
        }
        PenumbraConfig config = PenumbraConfig.get();
        double factor = 1.0 + (sessionZoom - 1.0) * progress;
        if (config.binocularsBreathingMotion && ClientComfort.motionAllowed()) {
            double breath = Math.sin(Util.getMillis() * (Math.PI * 2.0) / BREATH_PERIOD_MS);
            factor *= 1.0 + BREATH_ZOOM_AMPLITUDE * config.binocularsBreathingStrength * progress * breath;
        }
        return (float) factor;
    }

    /** {@code true} tant que le zoom a un effet visible (progression > 0,1 %). */
    public static boolean isZoomActive() {
        return getProgress() > 0.001f;
    }

    /**
     * Dérive horizontale de l'overlay due à la respiration, en pixels GUI
     * (±0,4 px × strength × progress, sinus lent désynchronisé). Retombe à 0
     * hors zoom ou quand {@code binocularsBreathingMotion} est désactivé.
     * Consommée par {@code BinocularsOverlayRenderer}.
     */
    public static float getBreathOffsetX() {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.binocularsBreathingMotion || !ClientComfort.motionAllowed()) {
            return 0.0f;
        }
        float progress = getProgress();
        if (progress <= 0.0f) {
            return 0.0f;
        }
        double phase = Util.getMillis() * (Math.PI * 2.0) / BREATH_OFFSET_X_PERIOD_MS + 1.3;
        return (float) (BREATH_OFFSET_X_PX * config.binocularsBreathingStrength
                * progress * Math.sin(phase));
    }

    /**
     * Dérive verticale de l'overlay due à la respiration, en pixels GUI
     * (±1,5 px × strength × progress, sinus lent ≈ 7 s). Retombe à 0 hors zoom
     * ou quand {@code binocularsBreathingMotion} est désactivé.
     * Consommée par {@code BinocularsOverlayRenderer}.
     */
    public static float getBreathOffsetY() {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.binocularsBreathingMotion || !ClientComfort.motionAllowed()) {
            return 0.0f;
        }
        float progress = getProgress();
        if (progress <= 0.0f) {
            return 0.0f;
        }
        double phase = Util.getMillis() * (Math.PI * 2.0) / BREATH_PERIOD_MS;
        return (float) (BREATH_OFFSET_Y_PX * config.binocularsBreathingStrength
                * progress * Math.sin(phase));
    }

    /**
     * Zoom à la molette (appelé par {@code MouseHandlerScrollMixin}). Si le zoom
     * est actif ET {@code binocularsScrollZoom} activé : ajuste le zoom de
     * session de ±{@value #SCROLL_STEP}, borné à [{@code binocularsMinZoom},
     * {@code binocularsMaxZoom}] (défauts 2.0/8.0), et retourne {@code true} —
     * l'événement est consommé, la barre d'inventaire ne défile pas. Sinon
     * retourne {@code false} et le défilement vanilla suit son cours.
     */
    public static boolean handleScroll(double yOffset) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.binocularsScrollZoom || yOffset == 0.0 || !isZoomActive()) {
            return false;
        }
        double min = Math.min(config.binocularsMinZoom, config.binocularsMaxZoom);
        double max = Math.max(config.binocularsMinZoom, config.binocularsMaxZoom);
        double next = sessionZoom + (yOffset > 0.0 ? SCROLL_STEP : -SCROLL_STEP);
        sessionZoom = Math.max(min, Math.min(max, next));
        return true;
    }

    /** Facteur sélectionné à la molette, affiché par le HUD du caméscope. */
    public static double getSessionZoom() {
        return sessionZoom;
    }

    /** Temps écoulé depuis l'activation courante, utilisé par le timecode VHS. */
    public static long getRecordingElapsedMillis() {
        if (recordingStartMillis <= 0L || state == ZoomState.IDLE) {
            return 0L;
        }
        return Math.max(0L, Util.getMillis() - recordingStartMillis);
    }

    /**
     * Niveau de batterie visuel (durabilité restante des jumelles). Si la
     * durabilité est désactivée ou l'objet absent, l'indicateur reste plein.
     */
    public static float getBatteryLevel() {
        PenumbraConfig config = PenumbraConfig.get();
        Minecraft client = Minecraft.getInstance();
        if (!config.binocularsDurabilityEnabled || client.player == null) {
            return 1.0f;
        }
        ItemStack stack = findBinoculars(client.player);
        if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f,
                1.0f - stack.getDamageValue() / (float) stack.getMaxDamage()));
    }

    private static void startZoom(LocalPlayer player, PenumbraConfig config) {
        beginTransition(ZoomState.ZOOMING_IN, 0.0f);
        sessionZoom = config.binocularsZoomLevel;
        recordingStartMillis = Util.getMillis();
        playLocal(player, PenumbraSounds.BINOCULARS_ON, config);
        sendState(new PenumbraC2S.BinocularsStateC2S(true));
    }

    /** Arrête le zoom : son, notification serveur, sortie immédiate ou lerée. */
    private static void stopZoom(LocalPlayer player, PenumbraConfig config, boolean immediate) {
        playLocal(player, PenumbraSounds.BINOCULARS_OFF, config);
        sendState(new PenumbraC2S.BinocularsStateC2S(false));
        if (immediate || !config.binocularsSmoothZoom) {
            // Interruption nette (écran ouvert, mort, 3e personne, changement de
            // monde) ou zoom non lissé : progression forcée à 0, binaire 0/1.
            state = ZoomState.IDLE;
            progressAtStateStart = 0.0f;
        } else {
            beginTransition(ZoomState.ZOOMING_OUT, rawProgress(Util.getMillis(), config));
        }
    }

    /**
     * Réinitialise tout l'état local (déconnexion, respawn, changement de monde).
     * Aucune restauration de FOV nécessaire : le mixin Camera divise uniquement
     * le champ calculé de la frame courante, jamais une valeur persistée.
     */
    private static void resetAll(Minecraft client) {
        boolean wasZooming = state != ZoomState.IDLE;
        state = ZoomState.IDLE;
        toggled = false;
        debugTicks = 0;
        progressAtStateStart = 0.0f;
        recordingStartMillis = 0L;
        inventoryOpen = false;
        if (wasZooming) {
            // Sans connexion (déconnexion), canSend renvoie false : aucun envoi.
            sendState(new PenumbraC2S.BinocularsStateC2S(false));
        }
    }

    /** Démarre une transition temporelle depuis la progression brute donnée. */
    private static void beginTransition(ZoomState target, float fromRawProgress) {
        state = target;
        stateStartMillis = Util.getMillis();
        progressAtStateStart = fromRawProgress;
    }

    /** Fait avancer la machine à états quand une transition atteint son terme. */
    private static void advanceState(PenumbraConfig config) {
        long now = Util.getMillis();
        if (state == ZoomState.ZOOMING_IN && rawProgress(now, config) >= 1.0f) {
            state = ZoomState.ZOOMED;
        } else if (state == ZoomState.ZOOMING_OUT && rawProgress(now, config) <= 0.0f) {
            state = ZoomState.IDLE;
        }
    }

    /**
     * Progression brute (0..1, non easée) calculée depuis les timestamps temps
     * réel. Entrée : {@code binocularsTransitionDuration} ms (défaut 300, borné
     * 250–400) ; sortie : 0,8× (borné 200–350). Zoom non lissé
     * ({@code binocularsSmoothZoom == false}) → binaire 0/1 immédiat.
     */
    private static float rawProgress(long now, PenumbraConfig config) {
        switch (state) {
            case IDLE:
                return 0.0f;
            case ZOOMED:
                return 1.0f;
            case ZOOMING_IN: {
                if (!config.binocularsSmoothZoom) {
                    return 1.0f;
                }
                float t = (now - stateStartMillis) / (float) transitionInMillis(config);
                if (t >= 1.0f) {
                    return 1.0f;
                }
                return progressAtStateStart + (1.0f - progressAtStateStart) * Math.max(0.0f, t);
            }
            case ZOOMING_OUT: {
                if (!config.binocularsSmoothZoom) {
                    return 0.0f;
                }
                float t = (now - stateStartMillis) / (float) transitionOutMillis(config);
                if (t >= 1.0f) {
                    return 0.0f;
                }
                return progressAtStateStart * (1.0f - Math.max(0.0f, t));
            }
            default:
                return 0.0f;
        }
    }

    /** Durée d'entrée du zoom en ms : config bornée à [250, 400]. */
    private static int transitionInMillis(PenumbraConfig config) {
        return Math.max(250, Math.min(400, config.binocularsTransitionDuration));
    }

    /** Durée de sortie du zoom en ms : 0,8× l'entrée, bornée à [200, 350]. */
    private static int transitionOutMillis(PenumbraConfig config) {
        int duration = Math.round(config.binocularsTransitionDuration * 0.8f);
        return Math.max(200, Math.min(350, duration));
    }

    /** {@code true} si les Jumelles sont quelque part dans l'inventaire du joueur. */
    private static boolean hasBinoculars(LocalPlayer player) {
        return !findBinoculars(player).isEmpty();
    }

    /** Première pile de jumelles trouvée, partagée par l'activation et la batterie. */
    private static ItemStack findBinoculars(LocalPlayer player) {
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() == PenumbraItems.BINOCULARS) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** Joue un clic mécanique local à {@code jumpscareVolume * 0.4}, jamais fort. */
    private static void playLocal(LocalPlayer player, SoundEvent sound, PenumbraConfig config) {
        float volume = Math.min(1.0f, config.jumpscareVolume * 0.4f);
        player.level().playLocalSound(player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, volume, 1.0f, false);
    }

    /** Envoie un payload C2S si la connexion accepte le canal (aucune écriture hors-jeu). */
    private static void sendState(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }

    /** Met à jour l'état d'inventaire et notifie le serveur sur changement uniquement. */
    private static void setInventoryOpen(boolean open) {
        if (inventoryOpen == open) {
            return;
        }
        inventoryOpen = open;
        sendState(new PenumbraC2S.InventoryStateC2S(open));
    }
}
