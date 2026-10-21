package fr.penumbra.entity.watcher;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registre auto-contenu de l'Observateur (id {@code penumbra:watcher}), Phase 4.
 * <p>
 * Reproduit le pattern de {@code PenumbraEntities} (vérifié en 26.1.2 :
 * {@code EntityType.Builder.of(...)} officiel + {@code build(ResourceKey)}, requis
 * depuis 1.21.2). L'intégrateur appelle {@link #register()} depuis
 * {@code PenumbraMod.onInitialize} : l'appel enregistre les attributs par défaut ET
 * force le chargement statique de la classe (donc l'enregistrement du type).
 * </p>
 */
public final class WatcherEntities {

    /** Clé de registre de l'entité Observateur. */
    private static final ResourceKey<EntityType<?>> WATCHER_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("watcher"));

    /**
     * Type d'entité de l'Observateur : hitbox 0.6×2.4 (humanoïde grand de ~2,4 blocs),
     * hauteur des yeux 2.1 (au milieu du visage), tracking 4 chunks.
     */
    public static final EntityType<WatcherEntity> WATCHER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("watcher"),
            EntityType.Builder.of(WatcherEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 2.4f)
                    .eyeHeight(2.1f)
                    .clientTrackingRange(4)
                    .build(WATCHER_KEY));

    private WatcherEntities() {
    }

    /**
     * Enregistre les attributs par défaut de l'Observateur. L'appel force aussi le
     * chargement statique de la classe (et donc l'enregistrement du type).
     */
    public static void register() {
        FabricDefaultAttributeRegistry.register(WATCHER, WatcherEntity.createWatcherAttributes());
    }
}
