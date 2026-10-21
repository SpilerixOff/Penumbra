package fr.penumbra.copy;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

/**
 * Sous-branche {@code copy} des commandes Penumbra (pattern Brigadier de
 * {@code PenumbraCommands}) : {@code copy build} force une copie imparfaite
 * (stade et cooldown ignorés, garde-fous de site respectés) et
 * {@code copy restore} restaure toutes les copies du niveau courant.
 * Permission niveau 2 (GAMEMASTERS), comme le reste de l'arbre.
 */
public final class CopyCommands {

    private CopyCommands() {
    }

    /** Attache la sous-branche {@code copy} à la racine donnée (ex. literal "penumbra"). */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("copy")
                .requires(source -> source.permissions()
                        .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.literal("build")
                        .executes(CopyCommands::build))
                .then(Commands.literal("restore")
                        .executes(CopyCommands::restore)));
    }

    /** /penumbra copy build : force maybeBuildCopy (hors cooldown/stade, site gardé). */
    private static int build(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (CopyManager.forceBuildCopy(player)) {
            source.sendSystemMessage(Component.translatable("penumbra.copy.built"));
            return 1;
        }
        source.sendFailure(Component.translatable("penumbra.copy.none"));
        return 0;
    }

    /** /penumbra copy restore : restaure toutes les copies du niveau courant. */
    private static int restore(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        int restored = CopyTracker.get(level).restoreAll(level);
        if (restored > 0) {
            source.sendSystemMessage(Component.translatable("penumbra.copy.restored", restored));
            return 1;
        }
        source.sendSystemMessage(Component.translatable("penumbra.copy.none"));
        return 0;
    }
}
