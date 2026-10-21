package net.fabricmc.fabric.api.client.model.loading.v1;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4fc;
public final class ModelModifier {
    @FunctionalInterface public interface AfterBakeItem {
        ItemModel modifyItemModelAfterBake(ItemModel model, Context context);
        interface Context {
            Identifier itemId();
            ItemModel.Unbaked sourceModel();
            ItemModel.BakingContext bakingContext();
            Matrix4fc transformation();
        }
    }
}
