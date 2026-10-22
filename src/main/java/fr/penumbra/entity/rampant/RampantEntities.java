package fr.penumbra.entity.rampant;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registre autonome du Rampant ({@code penumbra:rampant}). */
public final class RampantEntities {

    private static final ResourceKey<EntityType<?>> RAMPANT_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("rampant"));

    /**
     * Silhouette très basse et assez large pour rester lisible sur un plafond,
     * sans hitbox disproportionnée pendant les bonds.
     */
    public static final EntityType<RampantEntity> RAMPANT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("rampant"),
            EntityType.Builder.of(RampantEntity::new, MobCategory.MONSTER)
                    .sized(0.90f, 0.68f)
                    .eyeHeight(0.38f)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .build(RAMPANT_KEY));

    private RampantEntities() {
    }

    /** À appeler pendant l'initialisation commune, avant les ticks serveur. */
    public static void register() {
        FabricDefaultAttributeRegistry.register(RAMPANT, RampantEntity.createRampantAttributes());
    }
}
