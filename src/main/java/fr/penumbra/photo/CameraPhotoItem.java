package fr.penumbra.photo;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/** Une des quatre apparences d'une même photo en développement chimique. */
public final class CameraPhotoItem extends Item {

    private final int visualStage;

    public CameraPhotoItem(int visualStage, Properties properties) {
        super(properties);
        this.visualStage = visualStage;
    }

    public int visualStage() {
        return visualStage;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        PhotoData data = stack.get(PhotoContent.PHOTO_DATA);
        if (!(entity instanceof Player player) || data == null) {
            return;
        }
        int wantedStage = data.stage(level.getGameTime());
        if (wantedStage == visualStage) {
            return;
        }
        ItemStack replacement = stack.transmuteCopy(PhotoContent.photoItem(wantedStage));
        for (int index = 0; index < player.getInventory().getContainerSize(); index++) {
            if (player.getInventory().getItem(index) == stack) {
                player.getInventory().setItem(index, replacement);
                return;
            }
        }
        if (player.getItemBySlot(slot) == stack) {
            player.setItemSlot(slot, replacement);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        if (!PhotoContent.isPhoto(stack)) {
            return InteractionResult.FAIL;
        }
        return PhotoFramePlacement.place(context, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> consumer, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, consumer, flag);
        consumer.accept(Component.translatable("item.penumbra.camera_photo.stage." + visualStage)
                .withStyle(ChatFormatting.DARK_GRAY));
        consumer.accept(Component.translatable("item.penumbra.camera_photo.controls")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
