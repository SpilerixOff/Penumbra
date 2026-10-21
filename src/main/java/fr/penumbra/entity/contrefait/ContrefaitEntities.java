package fr.penumbra.entity.contrefait;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registre auto-contenu du Contrefait (id {@code penumbra:contrefait}), 0.6.0.
 * Reproduit le pattern de {@code WatcherEntities}/{@code OriginelEntities}
 * (vérifié en 26.1.2 : {@code EntityType.Builder.of(...)} +
 * {@code build(ResourceKey)}). L'intégrateur appelle {@link #register()}
 * depuis {@code PenumbraMod.onInitialize}.
 */
public final class ContrefaitEntities {

    /** Clé de registre de l'entité Contrefait. */
    private static final ResourceKey<EntityType<?>> CONTREFAIT_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("contrefait"));

    /**
     * Type d'entité du Contrefait : hitbox 0.6×1.95 (proportions de joueur,
     * comme la légende qu'il imite), yeux 1.62, tracking 4 chunks.
     */
    public static final EntityType<ContrefaitEntity> CONTREFAIT = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("contrefait"),
            EntityType.Builder.of(ContrefaitEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(4)
                    .build(CONTREFAIT_KEY));

    private ContrefaitEntities() {
    }

    /** Enregistre les attributs par défaut (force aussi le chargement statique). */
    public static void register() {
        FabricDefaultAttributeRegistry.register(CONTREFAIT, ContrefaitEntity.createContrefaitAttributes());
    }
}
