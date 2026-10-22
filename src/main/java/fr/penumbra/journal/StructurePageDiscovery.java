package fr.penumbra.journal;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Each investigator can recover a site's page from its chest, even after looting. */
public final class StructurePageDiscovery {
    private StructurePageDiscovery() {
    }

    public static void register() {
        UseBlockCallback.EVENT.register(StructurePageDiscovery::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand,
                                                BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer target)
                || hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isAlive()
                || !PenumbraConfig.get().journalEnabled
                || player.getEyePosition().distanceToSqr(Vec3.atCenterOf(hit.getBlockPos())) > 36.0
                || !(level.getBlockEntity(hit.getBlockPos()) instanceof ChestBlockEntity)) {
            return InteractionResult.PASS;
        }
        Optional<JournalEntry> page = recoverPage(JournalKnowledge.get(serverLevel.getServer()),
                player.getUUID(), StructureTracker.get(serverLevel.getServer()).all(),
                level.dimension(), hit.getBlockPos());
        if (page.isPresent()) {
            target.sendSystemMessage(Component.translatable("journal.penumbra.page_inserted",
                            Component.translatable(page.get().titleKey()))
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            PenumbraNetworking.sendSound(target, PenumbraSounds.PAGE_TURN,
                    target.position(), 0.5f, 0.88f);
        }
        // Leave both the container and its shared loot untouched; vanilla still opens it.
        return InteractionResult.PASS;
    }

    static Optional<JournalEntry> recoverPage(JournalKnowledge knowledge, UUID playerId,
                                              List<StructureTracker.Entry> structures,
                                              ResourceKey<Level> dimension, BlockPos clicked) {
        return pageAt(structures, dimension, clicked)
                .filter(page -> knowledge.markPageFound(playerId, page));
    }

    static Optional<JournalEntry> pageAt(List<StructureTracker.Entry> structures,
                                         ResourceKey<Level> dimension, BlockPos clicked) {
        for (StructureTracker.Entry structure : structures) {
            if (!structure.dimension().equals(dimension)) continue;
            StructureType type = StructureType.byId(structure.type());
            if (type == null || structure.variant() < 0 || structure.variant() >= type.variantCount()) continue;
            Optional<JournalEntry> page = JournalEntry.pageFor(type);
            if (page.isPresent() && chestPosition(structure, type).equals(clicked)) return page;
        }
        return Optional.empty();
    }

    /** These offsets mirror the existing builders, including anchors in older saves. */
    private static BlockPos chestPosition(StructureTracker.Entry structure, StructureType type) {
        return switch (type) {
            case ASH_SANCTUM -> structure.pos().offset(2, 0, 0);
            case PENUMBRA_WELL -> structure.variant() == 1
                    ? structure.pos().offset(3, -30, 0) : structure.pos().offset(0, -20, -3);
            default -> structure.pos(); // Post and house anchors are already their chests.
        };
    }
}
