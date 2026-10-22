package fr.penumbra.story;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.journal.JournalItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Every newcomer can read their own investigation, even on an already explored server. */
public final class StoryOnboarding {
    private static final Set<UUID> PENDING = new HashSet<>();

    private StoryOnboarding() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (!PenumbraConfig.get().journalEnabled) return;
            if (!deliver(handler.player)) {
                PENDING.add(handler.player.getUUID());
                handler.player.sendSystemMessage(Component.translatable("penumbra.story.journal_pending")
                        .withStyle(ChatFormatting.GRAY));
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (PENDING.isEmpty() || server.getTickCount() % 40 != 0
                    || !PenumbraConfig.get().journalEnabled) return;
            PENDING.removeIf(id -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                return player == null || player.isAlive() && !player.isSpectator() && deliver(player);
            });
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                PENDING.remove(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
    }

    private static boolean deliver(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()) return false;
        PenumbraPersistentState state = PenumbraPersistentState.get(player.level().getServer());
        return deliverOnce(state, player.getUUID(), () -> {
            if (hasBook(player)) return true;
            // Creative Inventory.add can discard an uninsertable stack and still return true.
            // Only the resulting inventory can confirm the player's book was delivered.
            player.getInventory().add(new ItemStack(JournalItems.SURVEY_JOURNAL));
            return hasBook(player);
        }, () -> player.sendSystemMessage(Component.translatable("penumbra.story.welcome")
                .withStyle(ChatFormatting.GRAY)));
    }

    private static boolean hasBook(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(JournalItems.SURVEY_JOURNAL)) return true;
        }
        return false;
    }

    /** Failed inventory delivery never consumes the player's persistent entitlement. */
    static boolean deliverOnce(PenumbraPersistentState state, UUID playerId,
                               BooleanSupplier ensureBook, Runnable welcome) {
        String key = "journal_received:" + playerId;
        if (state.isOneShotUsed(key)) return true;
        if (!ensureBook.getAsBoolean()) return false;
        state.markOneShot(key);
        welcome.run();
        return true;
    }
}
