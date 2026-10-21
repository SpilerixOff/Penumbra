package fr.penumbra.copy;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.story.StoryManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Point d'entrée du système de copies imparfaites.
 * <p>
 * {@link #maybeBuildCopy(ServerPlayer)} est appelé par le directeur depuis un
 * point rare (fréquence recommandée : ~1 appel toutes les 10 minutes par
 * joueur, probabilité 15 %) — aucune boucle par tick ici. Garde-fous internes :
 * config {@code imperfectCopiesEnabled}, stade d'histoire ≥ {@value #MIN_STAGE},
 * cooldown de {@value #COOLDOWN_TICKS} ticks (3 jours de jeu, persisté),
 * aucun autre joueur à moins de 30 blocs du joueur. Les garde-fous de site
 * (distance, blocs naturels, meubles du refuge, liquides) sont appliqués par
 * {@link CopyBuilder} dans tous les cas, y compris pour un déclenchement forcé.
 * </p>
 */
public final class CopyManager {

    private static final double DISCOVERY_RADIUS = 24.0;

    /** Cooldown entre deux copies pour un même joueur : 3 jours de jeu (72000 ticks). */
    public static final long COOLDOWN_TICKS = 72000L;
    /** Stade d'histoire minimal requis pour les copies imparfaites. */
    public static final int MIN_STAGE = 3;
    /** Distance minimale à tout autre joueur (30 blocs, au carré). */
    private static final double MIN_OTHER_PLAYER_DIST_SQR = 30.0 * 30.0;

    private CopyManager() {
    }

    /**
     * Point d'appel du directeur : tente une copie si tous les garde-fous
     * passent (config, stade ≥ 3, cooldown 3 jours, isolement social, site).
     * Ne fait rien la plupart du temps : coût nul quand les garde-fous échouent.
     */
    public static void maybeBuildCopy(ServerPlayer player) {
        tryBuild(player, false);
    }

    /**
     * Déclenchement forcé (commande {@code /penumbra copy build}) : ignore le
     * stade et le cooldown, mais PAS la config ni les garde-fous de site.
     *
     * @return vrai si une copie a été construite
     */
    public static boolean forceBuildCopy(ServerPlayer player) {
        return tryBuild(player, true);
    }

    /**
     * Restaure toutes les copies du niveau donné (contrat exposé à
     * {@code /penumbra debug restoration}, fourni par un autre agent).
     */
    public static void restoreAll(ServerLevel level) {
        CopyTracker.get(level).restoreAll(level);
    }

    /** Nombre de copies enregistrées pour ce joueur (helper de debug). */
    public static int count(ServerPlayer player) {
        return CopyTracker.get(player.level()).count(player.getUUID());
    }

    private static boolean tryBuild(ServerPlayer player, boolean force) {
        if (!PenumbraConfig.get().imperfectCopiesEnabled) {
            return false;
        }
        ServerLevel level = player.level();
        if (!force && StoryManager.getStage(player) < MIN_STAGE) {
            return false;
        }
        CopyTracker tracker = CopyTracker.get(level);
        long now = level.getGameTime();
        long lastCopy = tracker.lastCopyTick(player.getUUID());
        // lastCopy == 0 signifie « jamais construit » : pas de cooldown initial.
        if (!force && lastCopy != 0L && now - lastCopy < COOLDOWN_TICKS) {
            return false;
        }
        // Jamais en présence d'autres joueurs à moins de 30 blocs du joueur.
        for (ServerPlayer other : level.players()) {
            if (other != player && other.distanceToSqr(player) < MIN_OTHER_PLAYER_DIST_SQR) {
                return false;
            }
        }
        PlayerHorrorProfile profile =
                PenumbraPersistentState.get(level.getServer()).profile(player.getUUID());
        if (profile.homePoints().isEmpty()) {
            return false; // l'Hôte n'a encore rien appris à copier
        }
        if (!tracker.prepareForNewCopy(level)) {
            return false; // registre plein dans des chunks déchargés : réessayer plus tard
        }
        Optional<CopyBuilder.BuildResult> result = CopyBuilder.tryBuild(level, player, profile);
        if (result.isEmpty()) {
            return false;
        }
        tracker.record(level, player.getUUID(), result.get().site(), result.get().blocks());
        tracker.noteCopyBuilt(player.getUUID(), now);
        return true;
    }

    /** La progression n'est validée que lorsque le joueur découvre réellement la copie. */
    public static void tickDiscovery(ServerPlayer player) {
        if (CopyTracker.get(player.level()).markDiscoveredNear(player, DISCOVERY_RADIUS)) {
            StoryManager.noteDiscovery(player, StoryManager.KEY_COPY_BUILT);
        }
    }
}
