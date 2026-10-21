package fr.penumbra.content.item;

/** Calculs purs du Sceau de bannissement, séparés de l'initialisation Minecraft. */
final class AshWardPolicy {

    private AshWardPolicy() {
    }

    /** Filtre sphérique exact appliqué après la présélection AABB du niveau. */
    static boolean isWithinRadius(double distanceSqr, double radius) {
        return distanceSqr >= 0.0
                && radius >= 0.0
                && distanceSqr <= radius * radius;
    }

    /** Une apparition privée invisible ne peut être dissipée que par sa cible. */
    static boolean canBanishFor(boolean privateManifestation, boolean ownedByCaster) {
        return !privateManifestation || ownedByCaster;
    }
}
