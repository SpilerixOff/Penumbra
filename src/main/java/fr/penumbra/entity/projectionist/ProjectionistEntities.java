package fr.penumbra.entity.projectionist;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Registre autonome du Projectionniste photographique. */
public final class ProjectionistEntities {

    private static final ResourceKey<EntityType<?>> KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("projectionist"));

    public static final EntityType<ProjectionistEntity> PROJECTIONIST = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("projectionist"),
            EntityType.Builder.of(ProjectionistEntity::new, MobCategory.MONSTER)
                    .sized(0.62f, 2.5f)
                    .eyeHeight(2.18f)
                    .clientTrackingRange(6)
                    .updateInterval(2)
                    .build(KEY));

    private ProjectionistEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(
                PROJECTIONIST, ProjectionistEntity.createAttributes());
    }
}
