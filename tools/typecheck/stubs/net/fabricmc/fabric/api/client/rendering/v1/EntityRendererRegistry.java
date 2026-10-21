package net.fabricmc.fabric.api.client.rendering.v1;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
public final class EntityRendererRegistry {
    public static <E extends Entity> void register(EntityType<? extends E> type,
            EntityRendererProvider<E> provider) { }
}
