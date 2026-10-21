package fr.penumbra.journal;

import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * La Boussole d'arpenteur (0.19.0) — l'aiguille de Warrick.
 *
 * <p><b>Le problème qu'elle résout.</b> Le Bûcher est à onze cents ou deux mille
 * six cents blocs de l'origine, à une position dérivée de la graine. Sa colonne
 * de fumée ne se voit qu'à portée d'affichage. Autant dire qu'on peut jouer cent
 * heures sans jamais tomber dessus — et donc sans jamais trouver Le Relevé, qui
 * est pourtant la clé de tout le reste. Une direction fiable manquait.</p>
 *
 * <p><b>Comment l'aiguille tourne.</b> Aucun rendu sur mesure : on écrit la
 * cible dans le composant vanilla {@code lodestone_tracker}, avec le drapeau de
 * suivi à <i>faux</i> pour que le jeu ne vérifie pas qu'une pierre-repère existe
 * réellement là-bas. Le moteur fait alors tout le calcul d'angle, et la
 * définition d'objet choisit parmi trente-deux images. C'est le même mécanisme
 * qu'une boussole liée à une pierre-repère, détourné vers un lieu qui n'a rien
 * de particulier au sol.</p>
 *
 * <p>Le clic droit choisit le camp, un site inexploré ou le site le plus
 * proche. Seuls les sites déjà générés et le Bûcher déterministe sont connus.
 * Aucun chargement de terrain n'est nécessaire pour orienter l'aiguille.</p>
 */
public class SurveyCompassItem extends Item {

    /** Cadence de recalcul : une fois par seconde suffit largement. */
    private static final int REFRESH_TICKS = 20;

    public SurveyCompassItem(Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level,
                              Entity entity, EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof ServerPlayer player)
                || level.getGameTime() % REFRESH_TICKS != 0) {
            return;
        }
        refresh(stack, player, level);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        ItemStack stack = player.getItemInHand(hand);
        SurveyCompassRoute.Mode mode = mode(stack).next();
        stack.set(JournalItems.COMPASS_MODE, mode.ordinal());
        refresh(stack, serverPlayer, serverLevel);
        serverPlayer.sendOverlayMessage(Component.translatable(
                "item.penumbra.survey_compass.mode",
                Component.translatable("item.penumbra.survey_compass.mode." + mode.id())));
        player.getCooldowns().addCooldown(stack, 8);
        return InteractionResult.SUCCESS_SERVER;
    }

    private static SurveyCompassRoute.Mode mode(ItemStack stack) {
        return SurveyCompassRoute.Mode.fromIndex(stack.getOrDefault(JournalItems.COMPASS_MODE, 0));
    }

    private static void refresh(ItemStack stack, ServerPlayer player, ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            clear(stack);
            return;
        }

        SurveyCompassRoute.Site target = select(player, level, mode(stack));
        if (target == null) {
            clear(stack);
            return;
        }
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                // tracked = false : le jeu ne cherche pas de pierre-repère à
                // l'arrivée, il se contente de pointer vers les coordonnées.
                Optional.of(GlobalPos.of(level.dimension(),
                        new BlockPos(target.x(), target.y(), target.z()))), false));
        stack.set(JournalItems.COMPASS_TARGET, target.id());
        stack.set(JournalItems.COMPASS_DISTANCE, bracket(Math.sqrt(
                target.horizontalDistanceSquared(player.getX(), player.getZ()))));
    }

    private static void clear(ItemStack stack) {
        stack.remove(DataComponents.LODESTONE_TRACKER);
        stack.remove(JournalItems.COMPASS_TARGET);
        stack.remove(JournalItems.COMPASS_DISTANCE);
    }

    /** Coordinates from SavedData, plus an unbuilt camp; no distant getHeight call. */
    private static SurveyCompassRoute.Site select(ServerPlayer player, ServerLevel level,
                                                  SurveyCompassRoute.Mode mode) {
        StructureTracker tracker = StructureTracker.get(level.getServer());
        List<SurveyCompassRoute.Site> sites = new ArrayList<>();
        boolean campKnown = false;
        for (StructureTracker.Entry entry : tracker.all()) {
            if (!entry.dimension().equals(level.dimension())) {
                continue;
            }
            sites.add(new SurveyCompassRoute.Site(entry.type(), entry.pos().getX(),
                    entry.pos().getY(), entry.pos().getZ(),
                    tracker.hasDiscovered(player.getUUID(), entry)));
            campKnown |= entry.type().equals(StructureType.SIGNAL_PYRE.id());
        }
        if (!campKnown) {
            BlockPos pyre = PyreManager.resolvedSite(level);
            // The vanilla compass uses X/Z. A placeholder Y avoids generating
            // a distant chunk just to read a height that cannot affect its bearing.
            sites.add(new SurveyCompassRoute.Site(StructureType.SIGNAL_PYRE.id(),
                    pyre.getX(), 64, pyre.getZ(), false));
        }
        return SurveyCompassRoute.select(mode, sites, player.getX(), player.getZ()).orElse(null);
    }

    /**
     * Éloignement, en cinq paliers. Le carnet de Warrick ne donne jamais un
     * chiffre de jeu et la boussole non plus : elle dit « très loin », pas
     * « 1843 blocs ». Un instrument d'époque ne connaît pas la télémétrie.
     */
    private static int bracket(double distance) {
        if (distance < 48.0) {
            return 0;
        }
        if (distance < 220.0) {
            return 1;
        }
        if (distance < 700.0) {
            return 2;
        }
        if (distance < 1600.0) {
            return 3;
        }
        return 4;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display,
                                Consumer<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, lines, flag);
        lines.accept(Component.translatable("item.penumbra.survey_compass.mode",
                Component.translatable("item.penumbra.survey_compass.mode." + mode(stack).id())));
        lines.accept(Component.translatable("item.penumbra.survey_compass.controls"));
        String target = stack.getOrDefault(JournalItems.COMPASS_TARGET, "");
        if (target.isEmpty()) {
            lines.accept(Component.translatable(mode(stack) == SurveyCompassRoute.Mode.UNEXPLORED
                    ? "item.penumbra.survey_compass.no_new_site" : "item.penumbra.survey_compass.idle"));
            return;
        }
        lines.accept(Component.translatable("item.penumbra.survey_compass.target",
                Component.translatable("structure.penumbra." + target)));
        lines.accept(Component.translatable("item.penumbra.survey_compass.range_"
                + stack.getOrDefault(JournalItems.COMPASS_DISTANCE, 4)));
    }
}
