package fr.penumbra.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Caméra VHS historique, avec aide de contrôle du nouveau déclencheur photo. */
public final class VhsCameraItem extends Item {

    public VhsCameraItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> consumer, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, consumer, flag);
        consumer.accept(Component.translatable("item.penumbra.binoculars.controls")
                .withStyle(ChatFormatting.GRAY));
    }
}
