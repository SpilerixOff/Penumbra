package fr.penumbra.registry;

import fr.penumbra.PenumbraMod;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.photo.PhotoFrameEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registre des entités Penumbra. Déclare et enregistre l'Hôte (id {@code penumbra:host})
 * ainsi que ses attributs par défaut.
 */
public final class PenumbraEntities {

    /** Clé de registre de l'entité Hôte. */
    private static final ResourceKey<EntityType<?>> HOST_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("host"));
    private static final ResourceKey<EntityType<?>> PHOTO_FRAME_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("photo_frame"));

    /**
     * Type d'entité de l'Hôte : hitbox 0.6×2.6, hauteur des yeux 2.3, tracking 6 chunks.
     * Vérifié en 26.1.2 : EntityType.Builder.of(...) (officiel ; « create » est le nom Yarn)
     * et build(ResourceKey), requis depuis 1.21.2.
     */
    public static final EntityType<HostEntity> HOST = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("host"),
            EntityType.Builder.of(HostEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 2.6f)
                    .eyeHeight(2.3f)
                    .clientTrackingRange(6)
                    // Keep the hands, body and lifted player on the same tick in multiplayer.
                    .updateInterval(1)
                    .build(HOST_KEY));

    /** Petit cadre mural synchronisé comme un cadre d'objet, sans attributs vivants. */
    public static final EntityType<PhotoFrameEntity> PHOTO_FRAME = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("photo_frame"),
            EntityType.Builder.<PhotoFrameEntity>of(PhotoFrameEntity::new, MobCategory.MISC)
                    .sized(0.68f, 0.56f)
                    .clientTrackingRange(10)
                    .updateInterval(10)
                    .build(PHOTO_FRAME_KEY));

    private PenumbraEntities() {
    }

    /**
     * Enregistre les attributs par défaut de l'Hôte. L'appel force aussi le
     * chargement statique de la classe (et donc l'enregistrement du type).
     */
    public static void register() {
        FabricDefaultAttributeRegistry.register(HOST, HostEntity.createHostAttributes());
    }
}
