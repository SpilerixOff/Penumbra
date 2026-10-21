package fr.penumbra.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Objet narratif de Penumbra : un indice de structure sans fonction mécanique,
 * doté d'une ligne de description énigmatique dans son infobulle.
 * <p>La ligne est la clé de traduction {@code item.penumbra.<id>.desc}, rendue
 * en gris foncé italique (convention lore du mod). Les sous-classes peuvent
 * ajouter des lignes supplémentaires en surchargeant
 * {@link #appendHoverText} après {@code super}.</p>
 */
public class NarrativeItem extends Item {

    public NarrativeItem(Properties properties) {
        super(properties);
    }

    /**
     * Infobulle 26.1.2 : signature actuelle de {@code Item#appendHoverText},
     * vérifiée par javap sur minecraft-merged 26.1.2 —
     * {@code appendHoverText(ItemStack, Item.TooltipContext, TooltipDisplay,
     * Consumer<Component>, TooltipFlag)}. Les lignes sont poussées dans le
     * {@link Consumer} (plus de {@code List<Component>} depuis 1.21.5).
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> consumer, TooltipFlag flag) {
        consumer.accept(Component.translatable(this.getDescriptionId() + ".desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
