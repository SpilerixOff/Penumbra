package net.fabricmc.fabric.api.creativetab.v1;
import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
public final class CreativeModeTabEvents {
    @FunctionalInterface public interface ModifyEntries {
        void modify(Entries entries);
    }
    public interface Entries {
        void accept(net.minecraft.world.level.ItemLike item);
        void accept(ItemStack stack);
        void prepend(net.minecraft.world.level.ItemLike item);
        void prepend(ItemStack stack);
    }
    public static Event<ModifyEntries> modifyOutputEvent(ResourceKey<CreativeModeTab> tab) { return null; }
}
