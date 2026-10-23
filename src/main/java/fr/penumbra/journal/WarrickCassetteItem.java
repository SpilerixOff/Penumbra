package fr.penumbra.journal;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.story.StoryManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Damaged tape: mechanical sounds and five readable transcript fragments, not synthetic speech. */
public final class WarrickCassetteItem extends Item {
    private record Playback(ResourceKey<Level> dimension, long startTick) { }
    private static final Map<UUID, Playback> PLAYING = new HashMap<>();

    public WarrickCassetteItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer target) || !PenumbraConfig.get().journalEnabled)
            return InteractionResult.SUCCESS;
        if ((InvestigationProgress.volumesMask(target) & 8) == 0) {
            target.sendSystemMessage(Component.translatable("item.penumbra.warrick_cassette.need_book"));
            return InteractionResult.CONSUME;
        }
        if (!InvestigationProgress.hasItem(target, PenumbraItems.BINOCULARS)) {
            target.sendSystemMessage(Component.translatable("item.penumbra.warrick_cassette.need_camera"));
            return InteractionResult.CONSUME;
        }
        if (PLAYING.containsKey(target.getUUID())) return InteractionResult.CONSUME;
        PLAYING.put(target.getUUID(), new Playback(level.dimension(), level.getGameTime()));
        target.sendSystemMessage(Component.translatable("item.penumbra.warrick_cassette.play"));
        PenumbraNetworking.sendSound(target, PenumbraSounds.BINOCULARS_ON, target.position(), 0.6f, 0.7f);
        return InteractionResult.SUCCESS;
    }

    public static void registerPlayback() {
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYING.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PLAYING.clear());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var iterator = PLAYING.entrySet().iterator();
            while (iterator.hasNext()) {
                var session = iterator.next();
                ServerPlayer player = server.getPlayerList().getPlayer(session.getKey());
                Playback playback = session.getValue();
                if (player == null || !player.isAlive() || player.isSpectator()
                        || !PenumbraConfig.get().journalEnabled
                        || !player.level().dimension().equals(playback.dimension())
                        || !InvestigationProgress.hasItem(player, JournalItems.WARRICK_CASSETTE)
                        || !InvestigationProgress.hasItem(player, PenumbraItems.BINOCULARS)) {
                    iterator.remove();
                    if (player != null) player.sendOverlayMessage(Component.translatable(
                            "item.penumbra.warrick_cassette.interrupted"));
                    continue;
                }
                long elapsed = player.level().getGameTime() - playback.startTick();
                if (elapsed >= 20 && elapsed <= 260 && (elapsed - 20) % 60 == 0) {
                    int line = (int) ((elapsed - 20) / 60) + 1;
                    player.sendSystemMessage(Component.translatable("journal.penumbra.tape.line" + line));
                    PenumbraNetworking.sendSound(player, PenumbraSounds.BINOCULARS_OFF,
                            player.position(), 0.2f, 0.65f);
                }
                if (elapsed >= 300) {
                    iterator.remove();
                    InvestigationProgress.sync(player);
                    StoryManager.markFlag(player, InvestigationProgress.TAPE_HEARD);
                    InvestigationProgress.sync(player);
                    player.sendSystemMessage(Component.translatable("item.penumbra.warrick_cassette.finished"));
                }
            }
        });
    }
}
