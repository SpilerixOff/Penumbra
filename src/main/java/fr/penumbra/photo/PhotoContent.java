package fr.penumbra.photo;

import fr.penumbra.PenumbraMod;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Registre du composant, des quatre états photo et du petit cadre mural. */
public final class PhotoContent {

    public static final DataComponentType<PhotoData> PHOTO_DATA = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            PenumbraMod.id("photo_data"),
            DataComponentType.<PhotoData>builder()
                    .persistent(PhotoData.CODEC)
                    .networkSynchronized(PhotoData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    private static final ResourceKey<Item> PHOTO_BLACK_KEY = key("camera_photo_black");
    private static final ResourceKey<Item> PHOTO_FAINT_KEY = key("camera_photo_faint");
    private static final ResourceKey<Item> PHOTO_SEPIA_KEY = key("camera_photo_sepia");
    private static final ResourceKey<Item> PHOTO_KEY = key("camera_photo");
    private static final ResourceKey<Item> PHOTO_FRAME_KEY = key("photo_frame");

    public static final Item PHOTO_BLACK = registerPhoto(PHOTO_BLACK_KEY, 0);
    public static final Item PHOTO_FAINT = registerPhoto(PHOTO_FAINT_KEY, 1);
    public static final Item PHOTO_SEPIA = registerPhoto(PHOTO_SEPIA_KEY, 2);
    public static final Item PHOTO = registerPhoto(PHOTO_KEY, 3);
    public static final Item PHOTO_FRAME = Registry.register(
            BuiltInRegistries.ITEM,
            PHOTO_FRAME_KEY,
            new PhotoFrameItem(new Item.Properties().stacksTo(16).setId(PHOTO_FRAME_KEY)));

    private static final ResourceKey<CreativeModeTab> INGREDIENTS_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("ingredients"));

    private PhotoContent() {
    }

    private static ResourceKey<Item> key(String path) {
        return ResourceKey.create(Registries.ITEM, PenumbraMod.id(path));
    }

    private static Item registerPhoto(ResourceKey<Item> key, int stage) {
        return Registry.register(BuiltInRegistries.ITEM, key,
                new CameraPhotoItem(stage, new Item.Properties().stacksTo(1).setId(key)));
    }

    public static void register() {
        CreativeModeTabEvents.modifyOutputEvent(INGREDIENTS_TAB)
                .register(entries -> entries.accept(PHOTO_FRAME));
    }

    public static boolean isPhoto(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof CameraPhotoItem
                && stack.has(PHOTO_DATA);
    }

    public static Item photoItem(int stage) {
        return switch (Math.max(0, Math.min(3, stage))) {
            case 0 -> PHOTO_BLACK;
            case 1 -> PHOTO_FAINT;
            case 2 -> PHOTO_SEPIA;
            default -> PHOTO;
        };
    }

    public static ItemStack createPhoto(PhotoData data, long gameTime) {
        ItemStack stack = new ItemStack(photoItem(data.stage(gameTime)));
        stack.set(PHOTO_DATA, data);
        return stack;
    }
}
