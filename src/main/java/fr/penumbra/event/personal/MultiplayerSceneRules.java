package fr.penumbra.event.personal;

import java.util.List;

/**
 * Règles pures partagées par les scènes asymétriques. Les décisions de cible
 * restent testables sans lancer un serveur Minecraft.
 */
public final class MultiplayerSceneRules {

    private MultiplayerSceneRules() {
    }

    public static boolean eligibleCompanion(boolean samePlayer, boolean sameDimension,
                                            boolean alive, boolean spectator,
                                            double distance, double minimumDistance,
                                            double maximumDistance) {
        return !samePlayer && sameDimension && alive && !spectator
                && Double.isFinite(distance)
                && distance >= Math.max(0.0, minimumDistance)
                && distance <= Math.max(minimumDistance, maximumDistance);
    }

    /**
     * Classe les profils que L'Absent peut voler. Un autre joueur caché dans
     * la même dimension est le meilleur leurre ; un joueur de cette dimension
     * reste valable quelle que soit sa distance, puis un profil connecté dans
     * une autre dimension sert de dernier repli multijoueur. Le propriétaire
     * n'est jamais candidat et ne sera utilisé que lorsqu'il est réellement seul.
     */
    public static int imitationSourcePriority(boolean samePlayer,
                                               boolean sameDimension,
                                               boolean alive,
                                               boolean spectator,
                                               double distance,
                                               boolean clearlyVisible) {
        if (samePlayer || !alive || spectator) {
            return -1;
        }
        if (!sameDimension) {
            return 0;
        }
        return Double.isFinite(distance) && distance >= 12.0 && !clearlyVisible
                ? 2 : 1;
    }

    /**
     * Choisit un vrai nom connecté lorsqu'il existe, sinon fabrique l'alias
     * solo historique. {@code randomValue} peut être n'importe quel entier.
     */
    public static String chooseImitatedName(String ownerName, List<String> companionNames,
                                            int randomValue) {
        if (!companionNames.isEmpty()) {
            return companionNames.get(Math.floorMod(randomValue, companionNames.size()));
        }
        String name = ownerName == null ? "" : ownerName;
        if (name.isEmpty()) {
            return "Absent";
        }
        return switch (Math.floorMod(randomValue, 4)) {
            case 0 -> name + "?";
            case 1 -> "_" + name + "_";
            case 2 -> new StringBuilder(name).reverse().toString();
            default -> name.substring(0, name.length() - 1) + ".";
        };
    }
}
