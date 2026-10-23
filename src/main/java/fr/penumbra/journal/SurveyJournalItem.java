package fr.penumbra.journal;

import fr.penumbra.config.PenumbraConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Le Relevé (0.18.0) — le carnet de terrain d'un enquêteur mort depuis longtemps.
 *
 * <p>Clic droit : le serveur envoie l'état exact de la connaissance du joueur et
 * l'écran s'ouvre. Aucune donnée n'est stockée dans l'objet lui-même : céder le
 * carnet à quelqu'un d'autre ne lui transmet rien, il verra son propre relevé,
 * avec ses propres pages délavées. L'objet n'est qu'une reliure ; l'enquête est
 * dans la tête du joueur.</p>
 */
public class SurveyJournalItem extends Item {

    public SurveyJournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (!PenumbraConfig.get().journalEnabled) {
                serverPlayer.sendSystemMessage(
                        Component.translatable("journal.penumbra.disabled")
                                .withStyle(ChatFormatting.DARK_GRAY), true);
                return InteractionResult.CONSUME;
            }
            InvestigationProgress.recoverNearby(serverPlayer);
            if (player.isShiftKeyDown()) InvestigationProgress.recoverKnown(serverPlayer);
            JournalNetworking.open(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Infobulle : deux lignes d'ambiance, jamais un résumé du contenu. Le nombre
     * de pages lues n'y figure pas — c'est l'écran qui le dit, une fois ouvert.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> lines,
                                TooltipFlag flag) {
        lines.accept(Component.translatable("item.penumbra.survey_journal.lore1")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        lines.accept(Component.translatable("item.penumbra.survey_journal.lore2")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
