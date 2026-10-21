package fr.penumbra.story;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.util.Mth;

import java.util.Map;
import java.util.TreeMap;

/**
 * Sous-arbre {@code story} de la commande {@code /penumbra} (pattern
 * {@code PenumbraCommands}) ; l'intégrateur l'accroche via
 * {@code StoryCommands.attach(penumbraRoot)}.
 * <ul>
 *   <li>{@code story get} : stade et compteurs de découvertes du joueur exécutant
 *       (clé {@code penumbra.story.get}) ;</li>
 *   <li>{@code story set <0-6>} : permission niveau 2 (GAMEMASTERS), délègue à
 *       {@link StoryManager#setStage} puis confirme (clé {@code penumbra.story.set.done}).</li>
 * </ul>
 */
public final class StoryCommands {

    private StoryCommands() {
    }

    /** Accroche le sous-arbre {@code story} à la racine {@code /penumbra} fournie. */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("story")
                .then(Commands.literal("get")
                        .executes(StoryCommands::get))
                .then(Commands.literal("set")
                        .requires(source -> source.permissions()
                                .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, StoryStage.MAX_INDEX))
                                .executes(StoryCommands::set))));
    }

    /** /penumbra story get : stade + compteurs de découvertes du joueur exécutant. */
    private static int get(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int stage = StoryManager.getStage(player);
        source.sendSystemMessage(Component.translatable("penumbra.story.get",
                stage,
                StoryStage.byIndex(stage).id(),
                formatCounters(StoryManager.getCounters(player))));
        return 1;
    }

    /**
     * /penumbra story set <0-6> : fixe le stade du joueur exécutant. Le feedback
     * affiche la valeur réellement persistée (bornée), indépendante de
     * {@code storyEnabled} — contrairement à {@link StoryManager#getStage}.
     */
    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        int value = IntegerArgumentType.getInteger(context, "value");
        boolean changed = StoryManager.setStage(player, value);
        int clamped = Mth.clamp(value, 0, StoryStage.MAX_INDEX);
        source.sendSystemMessage(Component.translatable("penumbra.story.set.done",
                clamped, StoryStage.byIndex(clamped).id()));
        return changed ? 1 : 0;
    }

    /** Formate les compteurs pour l'affichage (triés par clé ; tiret cadratin si vide). */
    private static String formatCounters(Map<String, Integer> counters) {
        if (counters.isEmpty()) {
            return "—";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, Integer> entry : new TreeMap<>(counters).entrySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return builder.toString();
    }
}
