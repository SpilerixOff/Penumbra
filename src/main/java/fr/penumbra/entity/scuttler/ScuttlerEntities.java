package fr.penumbra.entity.scuttler;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registre autonome de la mini-araignée {@code penumbra:scuttler}. */
public final class ScuttlerEntities {

    private static final ResourceKey<EntityType<?>> KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("scuttler"));

    public static final EntityType<ScuttlerEntity> SCUTTLER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("scuttler"),
            EntityType.Builder.of(ScuttlerEntity::new, MobCategory.CREATURE)
                    .sized(0.68f, 0.34f)
                    .eyeHeight(0.22f)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build(KEY));

    private ScuttlerEntities() {
    }

    /** À appeler depuis l'initialiseur commun avant le démarrage du manager. */
    public static void register() {
        FabricDefaultAttributeRegistry.register(SCUTTLER, ScuttlerEntity.createAttributes());
    }
}
