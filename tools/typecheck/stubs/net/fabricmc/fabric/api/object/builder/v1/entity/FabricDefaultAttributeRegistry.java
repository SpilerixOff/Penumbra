package net.fabricmc.fabric.api.object.builder.v1.entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
public final class FabricDefaultAttributeRegistry {
    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier.Builder builder) { }
    public static void register(EntityType<? extends LivingEntity> type, AttributeSupplier supplier) { }
}
