package fr.penumbra.structure;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Arrays;
import java.util.Optional;

/**
 * Sous-arbre {@code /penumbra structure} (Phase 3) :
 * <ul>
 *   <li>{@code structure locate <type>} — structure la plus proche du type
 *       demandé, avec coordonnées et distance ;</li>
 *   <li>{@code structure spawn <type>} — (permission 2) construit la
 *       structure devant le joueur, sur la surface, et l'enregistre.</li>
 * </ul>
 * {@link #describeNearest(ServerPlayer)} sert l'intégration jumelles
 * ({@code /penumbra binoculars landmark}) : direction cardinale + distance
 * énigmatique de la structure la plus proche.
 */
public final class StructureCommands {

    /** Portée du décalage de construction devant le joueur (blocs). */
    private static final int SPAWN_AHEAD = 8;

    /** Suggestions des quatre identifiants de structures. */
    private static final SuggestionProvider<CommandSourceStack> TYPE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    Arrays.stream(StructureType.values()).map(StructureType::id), builder);

    /** Noms des huit directions cardinales (clés {@code penumbra.direction.*}). */
    private static final String[] DIRECTION_KEYS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private StructureCommands() {
    }

    /** Greffe le sous-arbre {@code structure} sur la racine {@code /penumbra}. */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("structure")
                .then(Commands.literal("locate")
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(TYPE_SUGGESTIONS)
                                .executes(StructureCommands::locate)))
                .then(Commands.literal("spawn")
                        .requires(source -> source.permissions()
                                .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests(TYPE_SUGGESTIONS)
                                .executes(StructureCommands::spawn))));
    }

    /** /penumbra structure locate <type> : position + distance de la plus proche. */
    private static int locate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        StructureType type = StructureType.byId(StringArgumentType.getString(context, "type"));
        if (type == null) {
            source.sendFailure(Component.translatable("penumbra.structure.unknown",
                    StringArgumentType.getString(context, "type")));
            return 0;
        }
        Optional<StructureTracker.Entry> nearest =
                StructureTracker.get(source.getServer()).nearest(player, type);
        if (nearest.isEmpty()) {
            source.sendSystemMessage(Component.translatable("penumbra.structure.none"));
            return 0;
        }
        StructureTracker.Entry entry = nearest.get();
        int distance = (int) Math.round(Math.sqrt(entry.pos().distSqr(player.blockPosition())));
        source.sendSystemMessage(Component.translatable("penumbra.structure.locate_result",
                Component.translatable("penumbra.structure.locate." + type.id()),
                entry.pos().getX(), entry.pos().getY(), entry.pos().getZ(), distance));
        return 1;
    }

    /**
     * /penumbra structure spawn <type> : construit la structure
     * {@value #SPAWN_AHEAD} blocs devant le joueur, sur la surface, et
     * l'enregistre au tracker (commande d'administration, chunks existants).
     */
    private static int spawn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        StructureType type = StructureType.byId(StringArgumentType.getString(context, "type"));
        if (type == null) {
            source.sendFailure(Component.translatable("penumbra.structure.unknown",
                    StringArgumentType.getString(context, "type")));
            return 0;
        }
        ServerLevel level = player.level();
        Direction facing = player.getDirection();
        BlockPos ahead = player.blockPosition().relative(facing, SPAWN_AHEAD);
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, ahead.getX(), ahead.getZ());
        BlockPos surface = new BlockPos(ahead.getX(), y, ahead.getZ());
        int variant = level.getRandom().nextInt(type.variantCount());
        BlockPos anchor = PenumbraStructures.build(type, level, surface, variant, level.getSeed());
        if (anchor == null) {
            source.sendFailure(Component.translatable("penumbra.structure.spawn_failed"));
            return 0;
        }
        StructureTracker.get(source.getServer()).record(type, anchor, level, variant);
        source.sendSystemMessage(Component.translatable("penumbra.structure.spawned",
                Component.translatable("penumbra.structure.locate." + type.id()),
                anchor.getX(), anchor.getY(), anchor.getZ()));
        return 1;
    }

    /**
     * Phrase énigmatique pour {@code /penumbra binoculars landmark} :
     * direction cardinale (8 points) + distance arrondie à 10 blocs de la
     * structure la plus proche du joueur dans sa dimension, ou la phrase
     * « rien » s'il n'existe aucune structure connue.
     */
    public static Component describeNearest(ServerPlayer player) {
        Optional<StructureTracker.Entry> nearest =
                StructureTracker.get(player.level().getServer()).nearest(player, null);
        if (nearest.isEmpty()) {
            return Component.translatable("penumbra.structure.none");
        }
        StructureTracker.Entry entry = nearest.get();
        double dx = entry.pos().getX() + 0.5 - player.getX();
        double dz = entry.pos().getZ() + 0.5 - player.getZ();
        int distance = Math.max(10,
                (int) Math.round(Math.sqrt(dx * dx + dz * dz) / 10.0) * 10);
        return Component.translatable("penumbra.landmark",
                Component.translatable("penumbra.direction." + cardinal(dx, dz)),
                distance);
    }

    /**
     * Direction cardinale en 8 points : l'angle 0 pointe au NORD (-Z en
     * coordonnées Minecraft), croissant vers l'est.
     */
    private static String cardinal(double dx, double dz) {
        double angle = Math.atan2(dx, -dz);
        int index = Math.floorMod((int) Math.round(angle * 4.0 / Math.PI), 8);
        return DIRECTION_KEYS[index];
    }
}
