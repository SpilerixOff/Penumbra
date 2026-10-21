package fr.penumbra.prop;

import fr.penumbra.PenumbraMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registre autonome de l'accessoire de décor {@code penumbra:mesh_prop}. */
public final class PropEntities {

    private static final ResourceKey<EntityType<?>> KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("mesh_prop"));

    /**
     * Hitbox volontairement petite et fixe : elle ne sert qu'à la sélection au
     * réticule, pas aux collisions. Le maillage affiché peut être bien plus
     * grand — c'est le rayon englobant du modèle qui décide de la visibilité,
     * pas cette boîte.
     */
    public static final EntityType<MeshPropEntity> MESH_PROP = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("mesh_prop"),
            EntityType.Builder.<MeshPropEntity>of(MeshPropEntity::new, MobCategory.MISC)
                    .sized(0.9f, 1.8f)
                    .clientTrackingRange(10)
                    // Rien ne bouge : une mise à jour toutes les 20 secondes suffit
                    // largement, et c'est autant de trafic réseau économisé.
                    .updateInterval(400)
                    .build(KEY));

    private PropEntities() {
    }

    /** Force le chargement statique. Appelé par {@code PenumbraMod.onInitialize}. */
    public static void register() {
    }
}
