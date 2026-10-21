package net.fabricmc.fabric.api.client.rendering.v1;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import java.util.function.Supplier;
public final class ModelLayerRegistry {
    public static void registerModelLayer(ModelLayerLocation layer, Supplier<LayerDefinition> supplier) { }
}
