package fr.penumbra.entity.originel;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/**
 * Registre auto-contenu de l'Originel (id {@code penumbra:originel}), 0.5.0.
 * <p>
 * Reproduit le pattern de {@code WatcherEntities} (vérifié en 26.1.2 :
 * {@code EntityType.Builder.of(...)} officiel + {@code build(ResourceKey)},
 * requis depuis 1.21.2). L'intégrateur appelle {@link #register()} depuis
 * {@code PenumbraMod.onInitialize} : l'appel enregistre les attributs par
 * défaut ET force le chargement statique de la classe (donc le type).
 * </p>
 */
public final class OriginelEntities {

    /** Clé de registre de l'entité Originel. */
    private static final ResourceKey<EntityType<?>> ORIGINEL_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, PenumbraMod.id("originel"));

    /**
     * Type d'entité de l'Originel : hitbox 0.6×1.95 (proportions de joueur —
     * c'est tout le malaise), yeux 1.62, tracking 6 chunks (96 blocs, suffisant
     * pour les apparitions lointaines à 60–90 blocs).
     */
    public static final EntityType<OriginelEntity> ORIGINEL = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            PenumbraMod.id("originel"),
            EntityType.Builder.of(OriginelEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)
                    .eyeHeight(1.62f)
                    .clientTrackingRange(6)
                    // La charge cave ne dure que 8–12 ticks : une mise à jour
                    // par tick évite qu'elle se résume à trois téléportations.
                    .updateInterval(1)
                    .build(ORIGINEL_KEY));

    private OriginelEntities() {
    }

    /**
     * Enregistre les attributs par défaut de l'Originel. L'appel force aussi le
     * chargement statique de la classe (et donc l'enregistrement du type).
     */
    public static void register() {
        FabricDefaultAttributeRegistry.register(ORIGINEL, OriginelEntity.createOriginelAttributes());
    }
}
