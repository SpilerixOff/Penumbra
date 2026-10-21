package net.fabricmc.fabric.api.client.model.loading.v1;
import net.fabricmc.fabric.api.event.Event;
public interface ModelLoadingPlugin {
    static void register(ModelLoadingPlugin plugin) { }
    void initialize(Context context);
    interface Context {
        Event<ModelModifier.AfterBakeItem> modifyItemModelAfterBake();
    }
}
