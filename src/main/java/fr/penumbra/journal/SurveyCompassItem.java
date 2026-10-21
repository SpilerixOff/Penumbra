package fr.penumbra.journal;

import fr.penumbra.structure.StructureTracker;
import fr.penumbra.structure.StructureType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

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
 * <p><b>Ce qu'elle vise.</b> Le site le plus proche parmi ceux que le monde
 * connaît. Le Bûcher compte toujours — sa position se calcule, elle n'a pas
 * besoin d'avoir été visitée — les autres structures seulement une fois
 * réellement générées. Hors de l'Overworld, la cible est retirée et l'aiguille
 * se fige : une boussole n'a rien à dire ailleurs.</p>
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
        if (level.dimension() != Level.OVERWORLD) {
            clear(stack);
            return;
        }

        Target target = nearest(player, level);
        if (target == null) {
            clear(stack);
            return;
        }
        stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(
                // tracked = false : le jeu ne cherche pas de pierre-repère à
                // l'arrivée, il se contente de pointer vers les coordonnées.
                Optional.of(GlobalPos.of(level.dimension(), target.pos())), false));
        stack.set(JournalItems.COMPASS_TARGET, target.id());
        stack.set(JournalItems.COMPASS_DISTANCE, bracket(target.distance()));
    }

    private static void clear(ItemStack stack) {
        stack.remove(DataComponents.LODESTONE_TRACKER);
        stack.remove(JournalItems.COMPASS_TARGET);
        stack.remove(JournalItems.COMPASS_DISTANCE);
    }

    private record Target(String id, BlockPos pos, double distance) {
    }

    /** Site connu le plus proche, Bûcher compris même s'il n'est pas encore bâti. */
    private static Target nearest(ServerPlayer player, ServerLevel level) {
        Target best = null;

        BlockPos pyre = PyreManager.resolvedSite(level);
        BlockPos pyreGround = new BlockPos(pyre.getX(),
                level.getHeight(Heightmap.Types.WORLD_SURFACE, pyre.getX(), pyre.getZ()),
                pyre.getZ());
        best = new Target(StructureType.SIGNAL_PYRE.id(), pyreGround,
                PyreManager.distanceTo(player, pyre));

        StructureTracker.Entry entry = StructureTracker.get(level.getServer())
                .nearest(player, null).orElse(null);
        if (entry != null && entry.dimension() == level.dimension()) {
            double distance = Math.sqrt(entry.pos().distToCenterSqr(
                    player.getX(), entry.pos().getY() + 0.5, player.getZ()));
            if (distance < best.distance()) {
                best = new Target(entry.type(), entry.pos(), distance);
            }
        }
        return best;
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
        String target = stack.getOrDefault(JournalItems.COMPASS_TARGET, "");
        if (target.isEmpty()) {
            lines.accept(Component.translatable("item.penumbra.survey_compass.idle"));
            return;
        }
        lines.accept(Component.translatable("item.penumbra.survey_compass.target",
                Component.translatable("structure.penumbra." + target)));
        lines.accept(Component.translatable("item.penumbra.survey_compass.range_"
                + stack.getOrDefault(JournalItems.COMPASS_DISTANCE, 4)));
    }
}
