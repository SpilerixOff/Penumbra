package fr.penumbra.content.item;

import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Objet narratif qui donne une direction approximative vers une structure déjà générée. */
public final class StructureClueItem extends NarrativeItem {

    private final StructureType targetType;

    public StructureClueItem(Properties properties, StructureType targetType) {
        super(properties);
        this.targetType = targetType;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        StructureTracker.get(level.getServer()).nearest(serverPlayer, this.targetType)
                .ifPresentOrElse(entry -> revealDirection(serverPlayer, entry),
                        () -> serverPlayer.sendSystemMessage(Component
                                .translatable("item.penumbra.clue.none")
                                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC)));
        return InteractionResult.SUCCESS;
    }

    private static void revealDirection(ServerPlayer player, StructureTracker.Entry entry) {
        double dx = entry.pos().getX() + 0.5 - player.getX();
        double dz = entry.pos().getZ() + 0.5 - player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        String direction = directionKey(dx, dz);
        String range = distance < 128.0 ? "near" : distance < 512.0 ? "far" : "very_far";
        player.sendSystemMessage(Component.translatable("item.penumbra.clue.reading",
                        Component.translatable("item.penumbra.clue.direction." + direction),
                        Component.translatable("item.penumbra.clue.range." + range))
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        double length = Math.max(1.0, Math.sqrt(dx * dx + dz * dz));
        Vec3 cue = player.position().add(dx / length * 4.0, 0.0, dz / length * 4.0);
        PenumbraNetworking.sendSound(player, PenumbraSounds.DISTANT_HUM,
                cue, 0.25f, 0.8f);
    }

    private static String directionKey(double dx, double dz) {
        if (Math.abs(dx) > Math.abs(dz) * 1.5) {
            return dx > 0.0 ? "east" : "west";
        }
        if (Math.abs(dz) > Math.abs(dx) * 1.5) {
            return dz > 0.0 ? "south" : "north";
        }
        if (dx > 0.0) {
            return dz > 0.0 ? "south_east" : "north_east";
        }
        return dz > 0.0 ? "south_west" : "north_west";
    }
}
