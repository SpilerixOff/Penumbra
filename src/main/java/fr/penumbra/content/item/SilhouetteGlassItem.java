package fr.penumbra.content.item;

import fr.penumbra.content.NarrativeItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * Morceau de verre ({@code silhouette_glass}) : éclat au reflet sombre dont
 * l'infobulle révèle parfois une ligne supplémentaire —
 * « une silhouette semble se refléter ».
 *
 * <h2>Détermination des 15&nbsp;% (approche retenue, 26.1.2)</h2>
 * <p>Le marquage est <b>par stack</b> et persistant, via le composant de
 * données {@link NarrativeItems#SILHOUETTE_STATE} ({@code penumbra:silhouette_state},
 * int : 1 = marqué, 2 = net) :</p>
 * <ul>
 *   <li>Le tirage est fait <b>une seule fois</b>, paresseusement, dans
 *   {@link #inventoryTick} côté serveur (15&nbsp;% de chance) dès que le stack
 *   entre dans un inventaire. Il couvre ainsi toutes les sources
 *   d'obtention — coffres de structures, main créative,
 *   {@code /penumbra item give} — sans toucher aux loot tables (possédées
 *   par un autre agent).</li>
 *   <li>Le composant est {@code persistent} (NBT, survit aux rechargements)
 *   et {@code networkSynchronized} (l'infobulle client le voit).</li>
 * </ul>
 * <p>Alternatives écartées : un aléatoire « vivant » dans
 * {@code appendHoverText} scintillerait à chaque frame ; un aléatoire seedé
 * sur l'UUID du joueur serait par joueur (et non par stack) ; deux variantes
 * d'item pollueraient l'onglet créatif et ne permettraient aucun contrôle
 * sur les issues de craft/loot.</p>
 */
public final class SilhouetteGlassItem extends NarrativeItem {

    /** Probabilité qu'un stack révèle la silhouette (15 %). */
    public static final float SILHOUETTE_CHANCE = 0.15f;

    /** Valeur du composant : la silhouette s'y reflète. */
    public static final int STATE_MARKED = 1;

    /** Valeur du composant : verre ordinaire (tirage effectué). */
    public static final int STATE_CLEAR = 2;

    public SilhouetteGlassItem(Properties properties) {
        super(properties);
    }

    /**
     * Tirage unique par stack, côté serveur. Signature 26.1.2 vérifiée par
     * javap : {@code inventoryTick(ItemStack, ServerLevel, Entity, EquipmentSlot)}.
     * Les deux issues (marqué / net) sont enregistrées pour ne jamais
     * relancer le tirage à chaque tick.
     */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (!stack.has(NarrativeItems.SILHOUETTE_STATE)) {
            stack.set(NarrativeItems.SILHOUETTE_STATE,
                    level.getRandom().nextFloat() < SILHOUETTE_CHANCE ? STATE_MARKED : STATE_CLEAR);
        }
    }

    /** Ajoute la ligne « silhouette » si le stack est marqué. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> consumer, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, consumer, flag);
        if (stack.getOrDefault(NarrativeItems.SILHOUETTE_STATE, STATE_CLEAR) == STATE_MARKED) {
            consumer.accept(Component.translatable("item.penumbra.silhouette_glass.desc_alt")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
