package fr.penumbra.client.animation;

/**
 * Courbes pures partagees par les modeles de monstres.
 *
 * <p>Elles ne dependent d'aucune classe client Minecraft : les poses restent
 * testables sans lancer le jeu et toute valeur venant d'une interpolation de
 * rendu est bornee avant d'atteindre un {@code ModelPart}.</p>
 */
public final class MonsterAnimationMath {

    private MonsterAnimationMath() {
    }

    public static float clamp01(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static float smoothstep(float value) {
        float p = clamp01(value);
        return p * p * (3.0f - 2.0f * p);
    }

    public static float smootherstep(float value) {
        float p = clamp01(value);
        return p * p * p * (p * (p * 6.0f - 15.0f) + 10.0f);
    }

    /**
     * Decompose une charge en trois gestes lisibles : recul preparatoire,
     * projection vers la cible, puis contraction tres breve a l'impact.
     */
    public static AttackPose attackPose(float progress) {
        float p = clamp01(progress);
        float anticipationIn = smootherstep(p / 0.20f);
        float anticipationOut = 1.0f - smootherstep((p - 0.20f) / 0.26f);
        float anticipation = anticipationIn * anticipationOut;
        float strike = smootherstep((p - 0.16f) / 0.72f);
        float impactPosition = clamp01((p - 0.76f) / 0.24f);
        float impact = (float) Math.sin(impactPosition * Math.PI);
        return new AttackPose(anticipation, strike, impact);
    }

    /**
     * Valeur 0 au-dela de {@code farDistance}, 1 a l'interieur de
     * {@code nearDistance}; utile pour tendre une silhouette avant son contact.
     */
    public static float proximity(double distanceSquared,
                                  float nearDistance, float farDistance) {
        if (!Double.isFinite(distanceSquared)
                || nearDistance < 0.0f || farDistance <= nearDistance) {
            return 0.0f;
        }
        double distance = Math.sqrt(Math.max(0.0, distanceSquared));
        float linear = (float) ((farDistance - distance) / (farDistance - nearDistance));
        return smootherstep(linear);
    }

    /** Respiration non sinusoidale, assez reguliere pour rester subtile. */
    public static float organicBreath(float time, float speed) {
        if (!Float.isFinite(time) || !Float.isFinite(speed)) {
            return 0.0f;
        }
        return (float) (Math.sin(time * speed) * 0.72
                + Math.sin(time * speed * 0.47 + 1.1) * 0.28);
    }

    /**
     * Enveloppe 0..1 d'un spasme bref dont la position varie a chaque cycle.
     *
     * <p>Un tremblement periodique devient previsible : celui-ci se produit une
     * fois par periode, mais a un instant pseudo-aleatoire derive du numero de
     * cycle et de {@code seed}. Deux entites avec des seeds differents ne
     * tressaillent jamais en meme temps ; la meme entite ne tressaille jamais
     * deux fois au meme moment du cycle.</p>
     *
     * @param time          temps en ticks (age de l'entite)
     * @param periodTicks   longueur d'un cycle, strictement positive
     * @param durationTicks duree du spasme, strictement inferieure a la periode
     * @param seed          graine stable par entite ou par usage
     */
    public static float twitchPulse(float time, float periodTicks,
                                    float durationTicks, int seed) {
        if (!Float.isFinite(time) || !Float.isFinite(periodTicks)
                || !Float.isFinite(durationTicks)
                || periodTicks <= 0.0f || durationTicks <= 0.0f
                || durationTicks >= periodTicks) {
            return 0.0f;
        }
        int cycle = (int) Math.floor(time / periodTicks);
        int hash = scramble(cycle * 668265263 + seed * 374761393);
        float slack = periodTicks - durationTicks;
        float start = (hash >>> 8 & 0xFFFF) / 65535.0f * slack;
        float local = time - cycle * periodTicks - start;
        if (local < 0.0f || local > durationTicks) {
            return 0.0f;
        }
        return (float) Math.sin(local / durationTicks * Math.PI);
    }

    /** Melange entier deterministe (finaliseur de type murmur). */
    private static int scramble(int value) {
        int x = value;
        x ^= x >>> 16;
        x *= 0x7feb352d;
        x ^= x >>> 15;
        x *= 0x846ca68b;
        x ^= x >>> 16;
        return x;
    }

    public record AttackPose(float anticipation, float strike, float impact) {
    }
}
