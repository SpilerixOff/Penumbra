package fr.penumbra.event.underground;

import fr.penumbra.event.HorrorEvent;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Fournisseur des événements souterrains de Penumbra (Phase 3).
 * <p>
 * Ids contractuels : {@code wall_knocks}, {@code false_vein}, {@code foreign_torch},
 * {@code deadend_breath}, {@code tunnel_silhouette}, {@code cave_face}, {@code pickaxe_echo},
 * {@code shifted_gallery}, {@code impossible_room}. Tous partagent le canRun commun
 * ({@link AbstractUndergroundEvent}) : joueur sous terre, éveillé, et
 * {@code undergroundEventsEnabled} actif.
 * </p>
 * <p>
 * API pour l'intégrateur :
 * </p>
 * <ul>
 *   <li>{@link #create()} — liste immuable des 9 événements, à ajouter au pool du
 *       directeur ({@code HorrorEvents.ALL} ou équivalent) ;</li>
 *   <li>{@link #byId(String)} — résolution par id pour {@code /penumbra event underground <id>} ;</li>
 *   <li>{@link #register()} — hooks Fabric du package, à appeler une fois à
 *       l'initialisation. Actuellement : {@code PlayerBlockBreakEvents.AFTER} →
 *       {@link PickaxeEchoEvent#noteMined} (mémoire de minage de l'écho de pioche) ;</li>
 *   <li>{@link UndergroundRestorationTracker#restoreAll(net.minecraft.server.level.ServerLevel)}
 *       — à brancher sur {@code /penumbra debug restoration} ;</li>
 *   <li>{@link UndergroundRestorationTracker#onServerTick(net.minecraft.server.MinecraftServer)}
 *       — maintenance throttlée (1/200 ticks), à enregistrer sur
 *       {@code ServerTickEvents.END_SERVER_TICK}.</li>
 * </ul>
 */
public final class UndergroundEvents {

    /** Liste immuable des événements souterrains, instanciés une seule fois. */
    private static final List<HorrorEvent> ALL = List.of(
            new WallKnocksEvent(),
            new FalseVeinEvent(),
            new ForeignTorchEvent(),
            new DeadendBreathEvent(),
            new TunnelSilhouetteEvent(),
            new CaveFaceEvent(),
            new PickaxeEchoEvent(),
            new ShiftedGalleryEvent(),
            new ImpossibleRoomEvent()
    );

    private UndergroundEvents() {
    }

    /**
     * Retourne la liste immuable des 9 événements souterrains (singletons sans état).
     * L'intégrateur l'ajoute au pool de sélection du directeur.
     */
    public static List<HorrorEvent> create() {
        return ALL;
    }

    /** Retourne l'événement portant cet id, ou {@code null} si inconnu. */
    public static HorrorEvent byId(String id) {
        for (HorrorEvent event : ALL) {
            if (event.id().equals(id)) {
                return event;
            }
        }
        return null;
    }

    /**
     * Enregistre les hooks Fabric des événements souterrains ; à appeler une fois à
     * l'initialisation du mod. Actuellement : après chaque bloc cassé par un joueur
     * ({@code PlayerBlockBreakEvents.AFTER}), la mémoire de minage de
     * {@code pickaxe_echo} est mise à jour. Le hook est en lecture seule : il ne
     * modifie ni le monde ni le drop du bloc.
     */
    public static void register() {
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                PickaxeEchoEvent.noteMined(serverPlayer, pos);
            }
        });
    }
}
