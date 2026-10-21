package fr.penumbra.entity.listener;

/**
 * Calculs purs de la portée du bruit et du rythme d'écoute de L'Écouteur,
 * isolés pour les tests (même convention que {@code ScuttlerBehavior}).
 *
 * <p>Contrat de conception : <b>l'immobilité totale est un silence total</b>.
 * Un joueur qui ne bouge pas produit une portée strictement nulle, et une
 * portée nulle n'est jamais audible, quelle que soit la distance — même à un
 * bloc. C'est la promesse qui rend la créature jouable : le joueur peut
 * apprendre à disparaître. Tout le reste du barème part de là.</p>
 */
final class ListenerNoise {

    /** Vitesse horizontale (blocs/tick) en dessous de laquelle un joueur est immobile. */
    static final double STILL_SPEED = 0.012;

    /** Portées de déplacement, en blocs. */
    static final double SPRINT_RADIUS = 22.0;
    static final double WALK_RADIUS = 9.0;
    static final double CROUCH_WALK_RADIUS = 2.5;

    /** Suppléments cumulatifs. */
    static final double SWING_BONUS = 7.0;
    static final double WATER_BONUS = 5.0;
    static final double MOUNT_BONUS = 8.0;

    /** Bruits ponctuels : ils s'entendent même depuis un joueur immobile. */
    static final double BLOCK_BREAK_RADIUS = 26.0;
    static final double HURT_RADIUS = 30.0;
    static final double FALL_RADIUS = 20.0;

    /** Portée maximale d'un bruit, quel que soit le cumul. */
    static final double MAX_RADIUS = 34.0;

    /** Distance sous laquelle un bruit franc autorise le verrouillage. */
    static final double LOCK_DISTANCE = 6.5;
    /** Portée minimale d'un bruit pour permettre un verrouillage. */
    static final double LOCK_MIN_RADIUS = WALK_RADIUS;

    /** Erreur de visée : il sait d'où vient le bruit à ±1,6 bloc, jamais mieux. */
    static final double AIM_ERROR = 1.6;

    /** Bornes de la phase d'écoute immobile. */
    static final int LISTEN_MIN_TICKS = 44;
    static final int LISTEN_MAX_TICKS = 76;

    /** Ticks de silence complet qui suffisent à casser un verrouillage. */
    static final int LOCK_GRACE_TICKS = 34;

    /** Nombre de destinations vaines avant qu'il renonce et s'éloigne. */
    static final int MAX_EMPTY_SWEEPS = 3;

    private ListenerNoise() {
    }

    /**
     * Portée du seul déplacement. Immobile = 0. Accroupi en mouvement = presque
     * rien. Marche = moyen. Sprint = très loin.
     */
    static double movementRadius(double horizontalSpeed, boolean sprinting, boolean crouching) {
        if (!(horizontalSpeed > STILL_SPEED)) {
            return 0.0;
        }
        if (crouching) {
            return CROUCH_WALK_RADIUS;
        }
        return sprinting ? SPRINT_RADIUS : WALK_RADIUS;
    }

    /**
     * Portée totale d'un joueur pour ce tick : déplacement plus suppléments.
     * Frapper ou miner s'entend même à l'arrêt — c'est le piège dans lequel
     * tombent tous les joueurs la première fois.
     */
    static double activityRadius(double movementRadius, boolean swinging,
                                 boolean inWater, boolean riding) {
        double radius = Math.max(0.0, movementRadius);
        if (swinging) {
            radius += SWING_BONUS;
        }
        if (inWater) {
            radius += WATER_BONUS;
        }
        if (riding) {
            radius += MOUNT_BONUS;
        }
        return Math.min(MAX_RADIUS, radius);
    }

    /** Un bruit de portée nulle n'existe pas ; sinon il porte jusqu'à sa portée. */
    static boolean audible(double radius, double distance) {
        return radius > 0.0 && distance <= radius;
    }

    /**
     * Verrouillage : uniquement un bruit franc (marche ou pire) émis très près.
     * Se déplacer accroupi à côté de lui ne le déclenche donc jamais.
     */
    static boolean lockable(double radius, double distance) {
        return radius >= LOCK_MIN_RADIUS && distance <= LOCK_DISTANCE;
    }

    /** Durée d'écoute immobile, bornée et pseudo-aléatoire. */
    static int listenDuration(int entropy) {
        return LISTEN_MIN_TICKS + Math.floorMod(
                entropy, LISTEN_MAX_TICKS - LISTEN_MIN_TICKS + 1);
    }

    /**
     * Erreur de visée appliquée à une coordonnée : décale la destination dans
     * {@code [-AIM_ERROR, +AIM_ERROR]}. Il vient vers le bruit, pas vers vous.
     */
    static double aimOffset(double unitRoll) {
        double clamped = Math.max(0.0, Math.min(1.0, unitRoll));
        return (clamped * 2.0 - 1.0) * AIM_ERROR;
    }

    /** Cooldown tolérant à un retour en arrière de l'horloge du monde. */
    static boolean cooldownReady(Long lastTick, long now, long cooldownTicks) {
        return lastTick == null || now < lastTick || now - lastTick >= cooldownTicks;
    }

    /** Dénominateur de chance, borné à 1 minimum (même formule que la Cavaleuse). */
    static int spawnDenominator(int base, float frequency, float intensity) {
        float pressure = Math.max(0.01f, frequency) * Math.max(0.01f, intensity);
        return Math.max(1, Math.round(Math.max(1, base) / pressure));
    }
}
