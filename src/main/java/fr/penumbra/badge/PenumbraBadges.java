package fr.penumbra.badge;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Badges de Penumbra : avancements du mod octroyés par code (SPEC2, phase 4).
 * <p>
 * Chaque badge est un avancement {@code penumbra:<badgeId>} dont l'unique critère
 * porte le déclencheur {@code minecraft:impossible} : le jeu ne le déclenche jamais
 * seul, il est accordé programmatiquement via
 * {@link net.minecraft.server.PlayerAdvancements#award(AdvancementHolder, String)}.
 * Le toast et le son vanilla d'avancement sont alors joués automatiquement.
 * </p>
 * <p>
 * API vérifiée par javap sur minecraft-merged.jar 26.1.2 :
 * {@code PlayerAdvancements.award(AdvancementHolder, String)},
 * {@code ServerAdvancementManager.get(Identifier)} (nullable),
 * {@code ServerPlayer.getAdvancements()},
 * {@code MinecraftServer.getAdvancements()},
 * {@code Identifier.fromNamespaceAndPath(String, String)}.
 * </p>
 * <p>
 * Appels rares (un événement de jeu précis) : aucune allocation par tick.
 * </p>
 */
public final class PenumbraBadges {

    /**
     * Nom de l'unique critère de chaque avancement Penumbra, identique dans tous
     * les JSON de {@code data/penumbra/advancement/}.
     */
    private static final String CRITERION_ID = "impossible";

    /** Ids de badges introuvables déjà signalés (un warn par id, jamais d'exception). */
    private static final Set<String> LOGGED_MISSING = ConcurrentHashMap.newKeySet();

    private PenumbraBadges() {
    }

    /** Journalise l'initialisation du module ; appelé par l'intégrateur au démarrage. */
    public static void register() {
        PenumbraMod.LOGGER.info("Badges Penumbra prêts — octroi par avancements (config badgesEnabled={}).",
                PenumbraConfig.get().badgesEnabled);
    }

    /**
     * Octroie le badge {@code penumbra:<badgeId>} au joueur.
     * <p>
     * Sortie silencieuse si {@code badgesEnabled} est désactivé dans la config.
     * Si l'avancement est absent du serveur (datapack non chargé, id erroné),
     * un avertissement est journalisé une seule fois par id et aucune exception
     * n'est levée. Sinon le critère impossible est accordé : toast et son vanilla
     * d'avancement sont joués automatiquement par le client.
     * </p>
     */
    public static void grant(ServerPlayer player, String badgeId) {
        if (!PenumbraConfig.get().badgesEnabled) {
            return;
        }
        AdvancementHolder holder = find(player, badgeId);
        if (holder == null) {
            warnMissingOnce(badgeId);
            return;
        }
        player.getAdvancements().award(holder, CRITERION_ID);
    }

    /**
     * Indique si le joueur a déjà complété l'avancement {@code penumbra:<badgeId>}
     * (anti-doublon côté appelant). Renvoie {@code false} si l'avancement est absent.
     */
    public static boolean has(ServerPlayer player, String badgeId) {
        if (player == null) {
            return false;
        }
        AdvancementHolder holder = find(player, badgeId);
        if (holder == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        return progress.isDone();
    }

    /** Retrouve l'avancement {@code penumbra:<badgeId>} auprès du serveur, ou {@code null}. */
    private static AdvancementHolder find(ServerPlayer player, String badgeId) {
        if (player == null || badgeId == null || badgeId.isEmpty()) {
            return null;
        }
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return null;
        }
        Identifier id = Identifier.fromNamespaceAndPath(PenumbraMod.MOD_ID, badgeId);
        return server.getAdvancements().get(id);
    }

    /** Journalise un avertissement une seule fois par id de badge introuvable. */
    private static void warnMissingOnce(String badgeId) {
        if (LOGGED_MISSING.add(badgeId)) {
            PenumbraMod.LOGGER.warn("Badge Penumbra introuvable : penumbra:{} "
                    + "(avancement absent — datapack non chargé ou id erroné). Octroi ignoré.", badgeId);
        }
    }
}
