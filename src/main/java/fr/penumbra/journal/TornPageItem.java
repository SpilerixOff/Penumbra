package fr.penumbra.journal;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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
 * Page arrachée (0.18.0) — une des quatre pages manquantes du Relevé.
 *
 * <p>Clic droit : la page se glisse dans le carnet et disparaît de l'inventaire.
 * Le joueur n'a <b>pas</b> besoin de posséder le Relevé pour l'absorber — s'il
 * trouve une page avant le carnet, la connaissance l'attend, et elle sera déjà
 * là le jour où il ouvrira la reliure. Rien n'est jamais perdu.</p>
 *
 * <p>Une page déjà glissée le dit et n'est pas consommée : le joueur ne peut pas
 * détruire une page par accident.</p>
 */
public class TornPageItem extends Item {

    public TornPageItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = player.getItemInHand(hand);
        JournalEntry entry = JournalItems.pageOf(stack);
        MinecraftServer server = serverPlayer.level().getServer();
        if (entry == null || server == null || !PenumbraConfig.get().journalEnabled) {
            // Page inerte (composant absent ou id d'une version future) : elle
            // reste dans l'inventaire, aucune donnée n'est inventée.
            serverPlayer.sendSystemMessage(
                    Component.translatable("journal.penumbra.page_illegible")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return InteractionResult.CONSUME;
        }

        JournalKnowledge knowledge = JournalKnowledge.get(server);
        if (knowledge.hasPage(serverPlayer.getUUID(), entry)) {
            serverPlayer.sendSystemMessage(
                    Component.translatable("journal.penumbra.page_already")
                            .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
            return InteractionResult.CONSUME;
        }

        knowledge.markPageFound(serverPlayer.getUUID(), entry);
        stack.shrink(1);
        serverPlayer.sendSystemMessage(
                Component.translatable("journal.penumbra.page_inserted",
                                Component.translatable(entry.titleKey()))
                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
        PenumbraNetworking.sendSound(serverPlayer, PenumbraSounds.PAGE_TURN,
                serverPlayer.position(), 0.5f, 0.88f);
        return InteractionResult.SUCCESS;
    }

    /**
     * Infobulle : les tout premiers mots de la page, et rien d'autre. Assez pour
     * donner envie, jamais assez pour rendre la lecture inutile.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> lines,
                                TooltipFlag flag) {
        JournalEntry entry = JournalItems.pageOf(stack);
        lines.accept(Component.translatable("item.penumbra.torn_page.desc")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        if (entry != null) {
            lines.accept(Component.translatable(entry.fadedKey())
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
    }
}
