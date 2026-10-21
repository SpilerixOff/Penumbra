package fr.penumbra.event;

import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import net.minecraft.server.level.ServerPlayer;

/**
 * Événement d'horreur environnementale déclenché par le {@code HorrorDirector}.
 * <p>
 * Contrat SPEC §4.6. Chaque implémentation est un singleton sans état, répertorié dans
 * {@link HorrorEvents#ALL}. Le directeur filtre par {@link #canRun(ServerPlayer, PlayerHorrorProfile)},
 * cooldown individuel et {@code wasRecent}, puis pondère via {@link #weight(TensionState)}.
 * </p>
 */
public interface HorrorEvent {

    /**
     * Identifiant unique de l'événement (ex. {@code "footsteps"}), utilisé par les commandes et le profil.
     */
    String id();

    /**
     * Poids de sélection pour l'état de tension donné ; 0 = jamais choisi dans cet état.
     */
    int weight(TensionState state);

    /**
     * Cooldown individuel en ticks avant que cet événement puisse se reproduire pour un même joueur.
     */
    int cooldownTicks();

    /**
     * Conditions contextuelles d'exécution (config, sommeil, environnement). Vérifié par le directeur
     * avant la sélection ; doit rester peu coûteux.
     */
    boolean canRun(ServerPlayer player, PlayerHorrorProfile profile);

    /**
     * Tente d'exécuter l'événement pour ce joueur (côté serveur). Les sons personnalisés passent
     * toujours par {@code PenumbraNetworking.sendSound} pour garantir la portée spatiale
     * multijoueur.
     *
     * @return {@code true} si un effet perceptible a réellement été produit ; {@code false} si le
     *         contexte final a invalidé l'événement (aucune porte, aucun emplacement sûr, etc.).
     *         Le directeur ne consomme historique, cooldown, progression et tension qu'en cas de
     *         succès.
     */
    boolean run(ServerPlayer player);
}
