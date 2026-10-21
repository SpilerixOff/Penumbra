package fr.penumbra.perception;

import fr.penumbra.director.TensionState;
import java.util.UUID;

/** Règles pures du système de perception individuelle 0.13. */
public final class PerceptionRules {

    public static final int MINIMUM_CONTENT_STAGE = 2;
    public static final int NOTICE_FOCUS_TICKS = 14;
    public static final int DISMISS_FOCUS_TICKS = 52;
    public static final int MIN_LIFETIME_TICKS = 200;
    public static final int MAX_LIFETIME_TICKS = 360;

    public enum Verification {
        UNAWARE,
        NOTICED,
        DISMISS
    }

    private PerceptionRules() {
    }

    public static boolean eligible(boolean perceptionsEnabled,
                                   boolean falseCompanionsEnabled,
                                   boolean alive, boolean spectator,
                                   boolean sleeping, boolean peaceful,
                                   boolean graceComplete,
                                   boolean automaticSceneAvailable,
                                   boolean ownerAlreadyBusy,
                                   boolean serverBudgetAvailable,
                                   int effectiveContentStage,
                                   TensionState tensionState) {
        return perceptionsEnabled && falseCompanionsEnabled
                && alive && !spectator && !sleeping && !peaceful
                && graceComplete && automaticSceneAvailable
                && !ownerAlreadyBusy && serverBudgetAvailable
                && effectiveContentStage >= MINIMUM_CONTENT_STAGE
                && tensionState != null
                && tensionState != TensionState.FALSE_CALM
                && tensionState.ordinal() >= TensionState.WATCHED.ordinal();
    }

    /** La caméra accélère uniquement une vérification déjà cadrée. */
    public static int nextFocusTicks(int current, boolean watched, boolean cameraActive) {
        int safe = Math.max(0, Math.min(DISMISS_FOCUS_TICKS, current));
        if (!watched) {
            return Math.max(0, safe - 2);
        }
        return Math.min(DISMISS_FOCUS_TICKS, safe + (cameraActive ? 3 : 1));
    }

    public static Verification verification(int focusTicks, boolean flashed) {
        if (flashed || focusTicks >= DISMISS_FOCUS_TICKS) {
            return Verification.DISMISS;
        }
        return focusTicks >= NOTICE_FOCUS_TICKS
                ? Verification.NOTICED : Verification.UNAWARE;
    }

    /** Une perception privée ne doit jamais être trackée par un autre UUID. */
    public static boolean mayObserve(UUID owner, UUID viewer, boolean individual) {
        if (viewer == null) {
            return false;
        }
        return !individual || owner != null && owner.equals(viewer);
    }

    public static int spawnDenominator(int baseOneIn, float frequency, float intensity) {
        float pressure = finitePositive(frequency) * finitePositive(intensity);
        if (pressure <= 0.0f) {
            return Integer.MAX_VALUE;
        }
        return Math.max(2, Math.round(Math.max(1, baseOneIn) / pressure));
    }

    /** Le maximum réduit le cooldown, sans permettre un spam plus fréquent que 45 s. */
    public static long cooldownTicks(int configuredSeconds, float frequency, float intensity) {
        float pressure = Math.max(0.25f,
                finitePositive(frequency) * finitePositive(intensity));
        long base = (long) Math.max(60, Math.min(1_800, configuredSeconds)) * 20L;
        return Math.max(20L * 45L, Math.round(base / Math.sqrt(pressure)));
    }

    public static int lifetimeTicks(long randomValue) {
        int range = MAX_LIFETIME_TICKS - MIN_LIFETIME_TICKS + 1;
        return MIN_LIFETIME_TICKS + (int) Math.floorMod(randomValue, range);
    }

    private static float finitePositive(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }
}
