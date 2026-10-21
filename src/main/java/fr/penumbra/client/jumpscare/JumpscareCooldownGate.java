package fr.penumbra.client.jumpscare;

/**
 * Deux verrous anti-spam indépendants : le verrou normal calme les effets
 * faibles, tandis que le verrou critique laisse toujours passer un screamer
 * important qui arrive pendant ce calme.
 */
final class JumpscareCooldownGate {

    private final int normalDuration;
    private final int criticalDuration;
    private int normalTicks;
    private int criticalTicks;
    private boolean currentCriticalWasForced;

    JumpscareCooldownGate(int normalDuration, int criticalDuration) {
        if (normalDuration < 0 || criticalDuration < 0) {
            throw new IllegalArgumentException("Les durées de cooldown doivent être positives");
        }
        this.normalDuration = normalDuration;
        this.criticalDuration = criticalDuration;
    }

    boolean tryAcquire(boolean critical) {
        return tryAcquire(critical, false);
    }

    /**
     * Le final d'une charge 3D est déjà protégé côté serveur par une séquence
     * longue et personnelle : il peut donc remplacer un visuel critique récent
     * au lieu d'être silencieusement perdu.
     */
    boolean tryAcquire(boolean critical, boolean forceCritical) {
        if (critical) {
            if (criticalTicks > 0) {
                // Le final d'une charge remplace une apparition ordinaire, mais
                // un doublon du même final ne doit jamais rejouer son cri.
                if (!forceCritical || currentCriticalWasForced) {
                    return false;
                }
            }
            criticalTicks = criticalDuration;
            currentCriticalWasForced = forceCritical;
            // Après un choc critique, empêcher un petit sting de se superposer.
            normalTicks = Math.max(normalTicks, normalDuration);
            return true;
        }
        if (normalTicks > 0) {
            return false;
        }
        normalTicks = normalDuration;
        return true;
    }

    void tick() {
        if (normalTicks > 0) {
            normalTicks--;
        }
        if (criticalTicks > 0) {
            criticalTicks--;
            if (criticalTicks == 0) {
                currentCriticalWasForced = false;
            }
        }
    }

    void reset() {
        normalTicks = 0;
        criticalTicks = 0;
        currentCriticalWasForced = false;
    }
}
