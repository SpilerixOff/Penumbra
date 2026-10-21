package fr.penumbra.content.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Calcul pur du plan 4×4 du marteau, séparé de l'Item pour les tests. */
final class HammerMiningPlane {

    private HammerMiningPlane() {
    }

    /**
     * Produit un offset perpendiculaire au regard : X/Z au sol ou plafond,
     * X/Y sur un mur nord-sud et Z/Y sur un mur est-ouest.
     */
    static BlockPos offset(BlockPos origin, Direction lookDirection,
                           int first, int second) {
        return switch (lookDirection.getAxis()) {
            case Y -> origin.offset(first, 0, second);
            case X -> origin.offset(0, second, first);
            case Z -> origin.offset(first, second, 0);
        };
    }
}
