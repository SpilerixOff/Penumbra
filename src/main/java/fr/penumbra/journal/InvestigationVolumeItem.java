package fr.penumbra.journal;

import fr.penumbra.config.PenumbraConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A readable, shareable volume; reading never consumes the original. */
public final class InvestigationVolumeItem extends Item {
    private final JournalEntry entry;

    public InvestigationVolumeItem(Properties properties, JournalEntry entry) {
        super(properties);
        this.entry = entry;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer target && PenumbraConfig.get().journalEnabled) {
            InvestigationProgress.discover(target, entry);
            JournalNetworking.open(target, entry.id());
        }
        return InteractionResult.SUCCESS;
    }
}
