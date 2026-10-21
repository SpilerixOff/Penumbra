package fr.penumbra.client.ambient;

import fr.penumbra.director.TensionState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Cache client de l'état de tension (0.5.0), alimenté par
 * {@code TensionSyncS2CPacket}. Jamais affiché : il pilote uniquement la
 * couche ambiante ({@link AmbientFearManager}, voile périphérique, dérive de
 * FOV). Sécurité : l'état retombe à {@code CALM} s'il devient périmé
 * (aucune synchro depuis {@value #STALE_TICKS} ticks — serveur sans le mod,
 * changement de monde) ou à la déconnexion ({@link #reset()}).
 */
@Environment(EnvType.CLIENT)
public final class ClientTensionState {

    /** Âge (ticks client) au-delà duquel la dernière synchro est périmée (15 s). */
    private static final int STALE_TICKS = 300;

    private static volatile TensionState state = TensionState.CALM;
    private static volatile float tension;
    private static int ticksSinceSync = STALE_TICKS;

    private ClientTensionState() {
    }

    /** Réception d'une synchro serveur (thread client, via {@code client.execute}). */
    public static void accept(int stateOrdinal, float syncedTension) {
        TensionState[] values = TensionState.values();
        if (stateOrdinal >= 0 && stateOrdinal < values.length) {
            state = values[stateOrdinal];
            tension = Math.max(0f, Math.min(100f, syncedTension));
            ticksSinceSync = 0;
        }
    }

    /** Tick client : vieillit la synchro (appelé par {@link AmbientFearManager}). */
    static void tick() {
        if (ticksSinceSync < STALE_TICKS) {
            ticksSinceSync++;
        }
    }

    /** Purge complète (déconnexion, changement de monde). */
    public static void reset() {
        state = TensionState.CALM;
        tension = 0f;
        ticksSinceSync = STALE_TICKS;
    }

    /** État courant, ou {@code CALM} si la synchro est périmée. */
    public static TensionState state() {
        return ticksSinceSync >= STALE_TICKS ? TensionState.CALM : state;
    }

    /** Tension courante 0–100, ou 0 si la synchro est périmée. */
    public static float tension() {
        return ticksSinceSync >= STALE_TICKS ? 0f : tension;
    }
}
