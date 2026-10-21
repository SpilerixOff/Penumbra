package fr.penumbra.content.item;

import fr.penumbra.event.personal.PersonalMoments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Journal incomplet (0.6.0) — l'objet narratif devient lisible. Au clic
 * droit, {@code PersonalMoments.onJournalUse} envoie au joueur des lignes
 * générées depuis les VRAIES statistiques de son profil d'horreur (nuits
 * dormies, regards arrière, minutes sous terre…) : le joueur découvre que le
 * narrateur du journal, c'est l'Hôte — et qu'il écrit sur LUI.
 * <p>
 * Signature 26.1.2 vérifiée par désassemblage du jar merged :
 * {@code Item.use(Level, Player, InteractionHand)} retourne
 * {@code InteractionResult} (plus d'{@code InteractionResultHolder} depuis
 * 1.21.2). Conserve l'infobulle lore de {@link NarrativeItem}.
 * </p>
 */
public class JournalItem extends NarrativeItem {

    public JournalItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND
                && player instanceof ServerPlayer serverPlayer) {
            PersonalMoments.onJournalUse(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }
}
