package fr.penumbra.entity.absent;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registre autonome de L'Absent ({@code penumbra:absent}).
 */
public final class AbsentEntities {

    private static final ResourceKey<EntityType<?>> ABSENT_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("absent"));
    private static final ResourceKey<EntityType<?>> FALSE_COMPANION_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("false_companion"));

    public static final EntityType<AbsentEntity> ABSENT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("absent"),
            EntityType.Builder.of(AbsentEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(6)
                    .build(ABSENT_KEY));

    /**
     * Type transitoire séparé : catégorie MISC afin qu'une hallucination privée
     * ne consomme jamais le mob-cap des vrais monstres du serveur.
     */
    public static final EntityType<AbsentEntity> FALSE_COMPANION = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("false_companion"),
            EntityType.Builder.of(AbsentEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .eyeHeight(1.62f)
                    .noSave()
                    .noSummon()
                    .noLootTable()
                    .clientTrackingRange(6)
                    .build(FALSE_COMPANION_KEY));

    private AbsentEntities() {
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(ABSENT, AbsentEntity.createAbsentAttributes());
        FabricDefaultAttributeRegistry.register(
                FALSE_COMPANION, AbsentEntity.createAbsentAttributes());
    }
}
