package fr.penumbra.network;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Couche réseau client → serveur de Penumbra (SPEC2 §3).
 * Suit l'état des jumelles et de l'inventaire de chaque joueur dans des maps
 * concurrentes, nettoyées automatiquement à la déconnexion.
 * Phase 4 : rate-limit global de {@value #RATE_MAX_PACKETS} paquets/seconde par
 * joueur (l'excédent est ignoré, avertissement au plus une fois par minute) et
 * coût de durabilité appliqué uniquement sur front montant du zoom — le serveur
 * ne fait jamais confiance au client pour déclencher quoi que ce soit : ces
 * états ne font qu'alimenter les sondages du directeur, qui décide seul.
 */
public final class PenumbraC2S {

    /** Fenêtre de comptage du rate-limit C2S (20 ticks = 1 seconde). */
    private static final long RATE_WINDOW_TICKS = 20L;
    /** Paquets C2S maximum acceptés par joueur et par fenêtre (Phase 4 : 10/s). */
    private static final int RATE_MAX_PACKETS = 10;
    /** Intervalle minimal entre deux avertissements de rate-limit, par joueur (1 min). */
    private static final long RATE_WARN_INTERVAL_TICKS = 1200L;

    private static final Map<UUID, Boolean> ZOOM_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> INVENTORY_STATES = new ConcurrentHashMap<>();
    /** Fenêtre courante de comptage par joueur : [début de fenêtre, paquets reçus]. */
    private static final Map<UUID, long[]> RATE_WINDOWS = new ConcurrentHashMap<>();
    /** Dernier avertissement de rate-limit émis, par joueur (tick de jeu). */
    private static final Map<UUID, Long> RATE_LAST_WARN = new ConcurrentHashMap<>();

    private PenumbraC2S() {
    }

    /** État du zoom jumelles d'un joueur. Consommé aussi pour la durabilité (SPEC2 §8). */
    public record BinocularsStateC2S(boolean zooming) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<BinocularsStateC2S> TYPE =
                new CustomPacketPayload.Type<>(PenumbraMod.id("binoculars_state"));

        public static final StreamCodec<? super RegistryFriendlyByteBuf, BinocularsStateC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, BinocularsStateC2S::zooming,
                        BinocularsStateC2S::new);

        @Override
        public CustomPacketPayload.Type<BinocularsStateC2S> type() {
            return TYPE;
        }
    }

    /** État d'ouverture d'un écran d'inventaire/conteneur côté client. */
    public record InventoryStateC2S(boolean open) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<InventoryStateC2S> TYPE =
                new CustomPacketPayload.Type<>(PenumbraMod.id("inventory_state"));

        public static final StreamCodec<? super RegistryFriendlyByteBuf, InventoryStateC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BOOL, InventoryStateC2S::open,
                        InventoryStateC2S::new);

        @Override
        public CustomPacketPayload.Type<InventoryStateC2S> type() {
            return TYPE;
        }
    }

    /**
     * Enregistre les deux payloads en phase play, sens client → serveur.
     * Vérifié en fabric-api 0.155.2+26.1.2 : le miroir de {@code clientboundPlay()}
     * est {@code PayloadTypeRegistry.serverboundPlay()}.
     */
    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(BinocularsStateC2S.TYPE, BinocularsStateC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(InventoryStateC2S.TYPE, InventoryStateC2S.CODEC);
    }

    /**
     * Enregistre les récepteurs serveur et le nettoyage à la déconnexion
     * ({@code ServerPlayConnectionEvents.DISCONNECT}, nom vérifié en 0.155.2).
     * Chaque récepteur passe d'abord par le rate-limit global ({@link #allowC2S}).
     * Le récepteur jumelles applique aussi le coût de durabilité (SPEC2 §8) :
     * 1 point par ACTIVATION si {@code binocularsDurabilityEnabled} et si le
     * joueur tient les Jumelles en main principale — activation = front montant
     * uniquement (Phase 4), pour qu'un client ne puisse pas vider la durabilité
     * en renvoyant « zooming=true » à répétition.
     */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(BinocularsStateC2S.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!allowC2S(player)) {
                return;
            }
            PenumbraConfig config = PenumbraConfig.get();
            boolean acceptedZoom = payload.zooming() && config.binocularsEnabled
                    && (!config.binocularsRequireItem || hasBinoculars(player));
            Boolean previous = ZOOM_STATES.put(player.getUUID(), acceptedZoom);
            boolean activation = acceptedZoom && (previous == null || !previous);
            boolean changed = previous == null ? acceptedZoom : previous != acceptedZoom;
            if (changed) {
                LinkedSceneManager.onCameraStateChanged(player, acceptedZoom);
                PenumbraNetworking.sendSoundNearbyExcept(
                        player,
                        player,
                        acceptedZoom ? PenumbraSounds.BINOCULARS_ON : PenumbraSounds.BINOCULARS_OFF,
                        player.position(),
                        Math.max(0.1f, config.jumpscareVolume * 0.35f),
                        1.0f);
            }
            if (activation && PenumbraConfig.get().binocularsDurabilityEnabled) {
                ItemStack held = player.getMainHandItem();
                if (held.getItem() == PenumbraItems.BINOCULARS) {
                    // ServerPlayer.level() retourne ServerLevel (vérifié en 26.1.2).
                    held.hurtAndBreak(1, player.level(), player,
                            item -> player.onEquippedItemBroken(item, EquipmentSlot.MAINHAND));
                }
            }
        });
        ServerPlayNetworking.registerGlobalReceiver(InventoryStateC2S.TYPE, (payload, context) -> {
            if (allowC2S(context.player())) {
                INVENTORY_STATES.put(context.player().getUUID(), payload.open());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> clear(handler.player.getUUID()));
    }

    /**
     * Rate-limit global C2S (Phase 4) : au plus {@value #RATE_MAX_PACKETS}
     * paquets par joueur et par fenêtre de {@value #RATE_WINDOW_TICKS} ticks ;
     * l'excédent est ignoré et journalisé en avertissement au plus une fois par
     * minute et par joueur. Le client légitime n'émet que sur transitions
     * (zoom, ouverture d'écran) : quelques paquets par seconde au pire — la
     * limite ne le contraint jamais. Le comptage est atomique
     * ({@code ConcurrentHashMap.compute}) car les récepteurs peuvent s'exécuter
     * hors du thread serveur.
     */
    static boolean allowC2S(ServerPlayer player) {
        UUID playerId = player.getUUID();
        long gameTime = player.level().getGameTime();
        boolean[] allowed = {false};
        RATE_WINDOWS.compute(playerId, (key, window) -> {
            if (window == null || gameTime < window[0] || gameTime - window[0] >= RATE_WINDOW_TICKS) {
                allowed[0] = true; // nouvelle fenêtre
                return new long[]{gameTime, 1L};
            }
            window[1]++;
            allowed[0] = window[1] <= RATE_MAX_PACKETS;
            return window;
        });
        if (!allowed[0]) {
            Long lastWarn = RATE_LAST_WARN.get(playerId);
            if (lastWarn == null || gameTime - lastWarn >= RATE_WARN_INTERVAL_TICKS) {
                RATE_LAST_WARN.put(playerId, gameTime);
                PenumbraMod.LOGGER.warn("[Penumbra] Rate-limit C2S dépassé par {} (> {}/s) : paquets ignorés",
                        player.getGameProfile().name(), RATE_MAX_PACKETS);
            }
        }
        return allowed[0];
    }

    /** {@code true} si le joueur zoome actuellement aux jumelles. */
    public static boolean isZooming(UUID playerId) {
        return ZOOM_STATES.getOrDefault(playerId, Boolean.FALSE);
    }

    /** {@code true} si le joueur a un écran d'inventaire/conteneur ouvert. */
    public static boolean isInventoryOpen(UUID playerId) {
        return INVENTORY_STATES.getOrDefault(playerId, Boolean.FALSE);
    }

    /** Validation serveur de la possession, utilisée quand l'option l'exige. */
    private static boolean hasBinoculars(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).getItem() == PenumbraItems.BINOCULARS) {
                return true;
            }
        }
        return false;
    }

    /** Oublie tous les états du joueur (appelé à la déconnexion et par /penumbra binoculars reset). */
    public static void clear(UUID playerId) {
        ZOOM_STATES.remove(playerId);
        INVENTORY_STATES.remove(playerId);
        RATE_WINDOWS.remove(playerId);
        RATE_LAST_WARN.remove(playerId);
    }

    /** Purge défensive entre deux mondes hébergés dans la même JVM. */
    public static void clearAll() {
        ZOOM_STATES.clear();
        INVENTORY_STATES.clear();
        RATE_WINDOWS.clear();
        RATE_LAST_WARN.clear();
    }
}
