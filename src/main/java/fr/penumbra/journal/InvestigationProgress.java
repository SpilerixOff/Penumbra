package fr.penumbra.journal;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.story.ConfrontationManager;
import fr.penumbra.story.StoryManager;
import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** All knowledge belongs to the investigator, never to the shared chest or held journal. */
public final class InvestigationProgress {
    public static final String STARTED = "investigation_v1";
    public static final String LEGACY_FINALE = "investigation_legacy_finale";
    public static final String TAPE_HEARD = "warrick_tape_heard";

    private InvestigationProgress() { }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sync(handler.player));
        WarrickCassetteItem.registerPlayback();
    }

    private static void initialize(ServerPlayer player) {
        if (!PenumbraConfig.get().storyEnabled || !PenumbraConfig.get().journalEnabled) return;
        boolean initialized = StoryManager.getCounters(player).getOrDefault(STARTED, 0) > 0;
        if (initialized) return;
        if (InvestigationRules.preserveLegacyFinale(StoryManager.getStage(player), false)) {
            StoryManager.markFlag(player, LEGACY_FINALE);
        }
        StoryManager.markFlag(player, STARTED);
    }

    public static int volumesMask(ServerPlayer player) {
        JournalKnowledge knowledge = JournalKnowledge.get(player.level().getServer());
        int mask = 0;
        for (int i = 0; i < InvestigationRules.VOLUMES.size(); i++) {
            if (knowledge.hasPage(player.getUUID(), InvestigationRules.VOLUMES.get(i))) mask |= 1 << i;
        }
        return mask;
    }

    public static boolean cassetteHeard(ServerPlayer player) {
        return StoryManager.getCounters(player).getOrDefault(TAPE_HEARD, 0) > 0;
    }

    public static boolean required(ServerPlayer player) {
        initialize(player);
        return PenumbraConfig.get().storyEnabled && PenumbraConfig.get().journalEnabled
                && !ConfrontationManager.isWon(player)
                && StoryManager.getCounters(player).getOrDefault(LEGACY_FINALE, 0) == 0;
    }

    public static boolean ready(ServerPlayer player) {
        return !required(player) || InvestigationRules.ready(volumesMask(player), cassetteHeard(player));
    }

    public static StructureType nextDestination(ServerPlayer player) {
        return InvestigationRules.destination(volumesMask(player), cassetteHeard(player));
    }

    public static void sync(ServerPlayer player) {
        initialize(player);
        if (!PenumbraConfig.get().storyEnabled || !PenumbraConfig.get().journalEnabled) return;
        int floor = InvestigationRules.stageFloor(volumesMask(player), cassetteHeard(player));
        if (floor > StoryManager.getStage(player)) StoryManager.setStage(player, floor);
    }

    public static void discover(ServerPlayer player, JournalEntry entry) {
        if (!InvestigationRules.VOLUMES.contains(entry) || !PenumbraConfig.get().journalEnabled) return;
        initialize(player); // Capture legacy status before the new books advance it.
        if (JournalKnowledge.get(player.level().getServer()).markPageFound(player.getUUID(), entry)) {
            player.sendSystemMessage(Component.translatable("journal.penumbra.page_inserted",
                    Component.translatable(entry.titleKey())));
        }
        sync(player);
    }

    /** Returns false on a full inventory, leaving the archive available for the next attempt. */
    public static boolean deliver(ServerPlayer player, Item item) {
        return ensureDelivered(() -> hasItem(player, item),
                () -> player.getInventory().add(new ItemStack(item)));
    }

    static boolean ensureDelivered(java.util.function.BooleanSupplier contains, Runnable insert) {
        if (contains.getAsBoolean()) return true;
        insert.run();
        return contains.getAsBoolean();
    }

    public static boolean hasItem(ServerPlayer player, Item item) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(item)) return true;
        }
        return false;
    }

    public static void recoverVolume(ServerPlayer player, JournalEntry entry) {
        discover(player, entry);
        boolean delivered = deliver(player, JournalItems.volumeItem(entry));
        if (entry == JournalEntry.LAST_PAGE) delivered &= deliver(player, JournalItems.WARRICK_CASSETTE);
        if (!delivered) player.sendOverlayMessage(Component.translatable("journal.penumbra.archive.full"));
    }

    /** Sneak-use the journal to rebind previously discovered volumes, including a lost cassette. */
    public static void recoverKnown(ServerPlayer player) {
        int mask = volumesMask(player);
        boolean delivered = true;
        for (int i = 0; i < 4; i++) {
            if ((mask & (1 << i)) != 0) delivered &= deliver(player,
                    JournalItems.volumeItem(InvestigationRules.VOLUMES.get(i)));
        }
        if ((mask & 8) != 0) delivered &= deliver(player, JournalItems.WARRICK_CASSETTE);
        player.sendOverlayMessage(Component.translatable(delivered
                ? "journal.penumbra.archive.recovered" : "journal.penumbra.archive.full"));
    }

    /** A destroyed chest cannot erase its archive. Must stand at the real archive, including well depth. */
    public static void recoverNearby(ServerPlayer player) {
        for (StructureTracker.Entry site : StructureTracker.get(player.level().getServer()).all()) {
            if (!site.dimension().equals(player.level().dimension())) continue;
            StructureType type = StructureType.byId(site.type());
            if (type == null || site.variant() < 0 || site.variant() >= type.variantCount()) continue;
            var page = JournalEntry.pageFor(type);
            if (page.isPresent() && player.position().distanceToSqr(
                    Vec3.atCenterOf(StructurePageDiscovery.chestPosition(site, type))) <= 16) {
                recoverVolume(player, page.get());
                return;
            }
        }
    }

    public static void explainMissing(ServerPlayer player) {
        int missing = InvestigationRules.firstMissing(volumesMask(player));
        player.sendSystemMessage(Component.translatable(missing >= 0
                ? "journal.penumbra.guide.investigation.book" + (missing + 1)
                : "journal.penumbra.guide.investigation.tape"));
    }
}
