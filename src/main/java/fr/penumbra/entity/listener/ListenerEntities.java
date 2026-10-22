package fr.penumbra.entity.listener;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registre autonome de L'Écouteur {@code penumbra:listener}.
 * Silhouette de 2,9 blocs — la plus haute du mod — et volontairement très fine :
 * de loin on la prend pour un tronc mort.
 */
public final class ListenerEntities {

    private static final ResourceKey<EntityType<?>> KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("listener"));

    public static final EntityType<ListenerEntity> LISTENER = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("listener"),
            EntityType.Builder.of(ListenerEntity::new, MobCategory.MONSTER)
                    .sized(0.55f, 2.9f)
                    .eyeHeight(2.6f)
                    .clientTrackingRange(6)
                    .updateInterval(1)
                    .build(KEY));

    private ListenerEntities() {
    }

    /** À appeler depuis l'initialiseur commun avant le démarrage du manager. */
    public static void register() {
        FabricDefaultAttributeRegistry.register(LISTENER, ListenerEntity.createAttributes());
    }
}
