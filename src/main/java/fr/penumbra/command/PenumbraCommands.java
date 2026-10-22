package fr.penumbra.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.penumbra.client.binoculars.BinocularsManager;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.content.ItemCommands;
import fr.penumbra.copy.CopyCommands;
import fr.penumbra.copy.CopyManager;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.DirectorMemory;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.director.sequence.LinkedSceneType;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.entity.absent.AbsentManager;
import fr.penumbra.entity.absent.KnownIdentities;
import fr.penumbra.entity.fauxmob.FauxMobManager;
import fr.penumbra.entity.fauxmob.FauxMobRules;
import fr.penumbra.entity.listener.ListenerManager;
import fr.penumbra.entity.originel.OriginelManager;
import fr.penumbra.entity.projectionist.ProjectionistEntity;
import fr.penumbra.entity.rampant.RampantManager;
import fr.penumbra.entity.scuttler.ScuttlerManager;
import fr.penumbra.entity.watcher.WatcherEntity;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.HorrorEvents;
import fr.penumbra.event.night.NightEvents;
import fr.penumbra.event.underground.UndergroundEvents;
import fr.penumbra.event.underground.UndergroundRestorationTracker;
import fr.penumbra.journal.PyreManager;
import fr.penumbra.network.PenumbraC2S;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.perception.IndividualPerceptionManager;
import fr.penumbra.prop.MeshPropEntity;
import fr.penumbra.story.StoryCommands;
import fr.penumbra.story.StoryManager;
import fr.penumbra.structure.StructureCommands;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Commandes d'administration {@code /penumbra} (permission niveau 2).
 * <p>Phase 1 : {@code status}, {@code tension <0-100>}, {@code event <id>},
 * {@code spawnhost}, {@code jumpscare <type>},
 * {@code reset}.</p>
 * <p>Phase 2 (SPEC2 §10) : {@code chase}, {@code scare <id>},
 * {@code binoculars test|host_sighting|reaction <type>|reset}.</p>
 * <p>Phase 3 : {@code event night|underground <id>}, {@code binoculars landmark},
 * {@code debug restoration}, et les sous-arbres greffés {@code story},
 * {@code structure}, {@code item}, {@code copy}.</p>
 * <p>Phase 4/5 : {@code watcher test} et {@code originel test}. Depuis 0.8.1,
 * les commandes de scène acceptent un argument final {@code [target]} : sans
 * argument elles visent l'exécutant, avec lui elles peuvent viser un autre
 * joueur ou être lancées depuis la console.</p>
 * <p>0.7.0 : {@code projectionist test}, {@code rampant test} et
 * {@code absent test} pour valider les trois nouvelles manifestations.</p>
 * <p>Les retours utilisent les clés de traduction {@code command.penumbra.*}
 * (définies par Agent_Resources) ; les messages d'erreur hors contrat restent
 * des littéraux (précédent Phase 1).</p>
 */
public final class PenumbraCommands {

    private static final String TARGET_ARGUMENT = "target";

    /** Types de jumpscare reconnus par le client (JumpscareManager). */
    private static final List<String> JUMPSCARE_TYPES =
            List.of("sting", "fake_creeper", "apparition", "screamer",
                    "rampant_screamer", "absent_screamer", "chorus_screamer",
                    "refuge_screamer", "mimic_screamer", "listener_screamer");

    /** Profils d'accessibilité 0.17.0. */
    private static final List<String> PROFILE_IDS =
            List.of("gentle", "standard", "nightmare");

    /** Ids des jumpscares retardés du directeur (SPEC2 §6). */
    private static final List<String> SCARE_IDS = List.of(
            "chest_scare", "refuge_scare", "torch_scare",
            "lookback_scare", "inventory_scare", "quiet_scare");

    /** Types de réactions de l'Hôte aux jumelles (SPEC2 §4). */
    private static final List<String> BINOCULAR_REACTIONS = List.of(
            "freeze", "head_turn", "vanish_on_unzoom", "approach_between",
            "sound_behind", "mimic_posture", "reappear_checked");

    private static final List<String> ARC_THEMES = List.of(
            "cave", "refuge", "stalker", "mimic", "photographic");

    private static final List<String> LINKED_SCENE_TYPES = List.of(
            "false_guide", "skitter_omen", "negative_witness");

    /**
     * Maillages livrés avec le mod (0.19.0).
     *
     * <p>Cette liste ne sert qu'à l'autocomplétion. Le serveur ne valide rien :
     * les maillages vivent dans les ressources <b>client</b>, et un pack de
     * ressources peut en ajouter — ceux-là ne seront pas suggérés, mais
     * fonctionneront si on tape leur nom.</p>
     */
    private static final List<String> PROP_MESHES = List.of(
            "borne", "cairn", "campement", "chaise", "croix", "effigie", "effigy",
            "miroir", "nid", "porte", "poupee", "souche", "theodolite");

    /** Durée du zoom forcé par {@code /penumbra binoculars test} (3 s). */
    private static final int DEBUG_ZOOM_TICKS = 60;

    /** Rayon de recherche d'un Hôte existant pour /penumbra chase (blocs). */
    private static final double CHASE_HOST_SEARCH_RADIUS = 60.0;

    /** Rayon de recherche d'un Hôte pour les réactions jumelles (blocs, SPEC2 §6). */
    private static final double REACTION_HOST_SEARCH_RADIUS = 100.0;

    /** Suggestions des identifiants d'événements enregistrés. */
    private static final SuggestionProvider<CommandSourceStack> EVENT_ID_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(HorrorEvents.ALL.stream().map(HorrorEvent::id), builder);

    /** Suggestions des identifiants d'événements nocturnes (Phase 3). */
    private static final SuggestionProvider<CommandSourceStack> NIGHT_EVENT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(NightEvents.create().stream().map(HorrorEvent::id), builder);

    /** Suggestions des identifiants d'événements souterrains (Phase 3). */
    private static final SuggestionProvider<CommandSourceStack> UNDERGROUND_EVENT_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(UndergroundEvents.create().stream().map(HorrorEvent::id), builder);

    /** Suggestions des types de jumpscare. */
    private static final SuggestionProvider<CommandSourceStack> JUMPSCARE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(JUMPSCARE_TYPES, builder);

    /** Suggestions des profils d'accessibilité. */
    private static final SuggestionProvider<CommandSourceStack> PROFILE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(PROFILE_IDS, builder);

    /** Suggestions des maillages de décor (0.19.0). */
    private static final SuggestionProvider<CommandSourceStack> PROP_MESH_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(PROP_MESHES, builder);

    /** Suggestions des ids de jumpscares retardés. */
    private static final SuggestionProvider<CommandSourceStack> SCARE_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(SCARE_IDS, builder);

    /** Suggestions des types de réaction aux jumelles. */
    private static final SuggestionProvider<CommandSourceStack> REACTION_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(BINOCULAR_REACTIONS, builder);

    private static final SuggestionProvider<CommandSourceStack> ARC_THEME_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(ARC_THEMES, builder);

    private static final SuggestionProvider<CommandSourceStack> LINKED_SCENE_SUGGESTIONS =
            (context, builder) -> SharedSuggestionProvider.suggest(LINKED_SCENE_TYPES, builder);

    private PenumbraCommands() {
    }

    /** Enregistre l'arbre /penumbra via le callback Fabric. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("penumbra")
                    .requires(source -> source.permissions()
                            .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                    .then(Commands.literal("status")
                            .executes(PenumbraCommands::status)
                            .then(targetArgument(PenumbraCommands::status)))
                    .then(Commands.literal("pyre")
                            .executes(PenumbraCommands::pyreStatus)
                            .then(Commands.literal("build")
                                    .executes(PenumbraCommands::pyreBuild))
                            .then(Commands.literal("tp")
                                    .executes(PenumbraCommands::pyreTeleport))
                            .then(Commands.literal("reset")
                                    .executes(PenumbraCommands::pyreReset)))
                    .then(Commands.literal("diag")
                            .executes(PenumbraCommands::diagnose)
                            .then(targetArgument(PenumbraCommands::diagnose)))
                    .then(Commands.literal("tension")
                            .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                    .executes(PenumbraCommands::setTension)
                                    .then(targetArgument(PenumbraCommands::setTension))))
                    .then(Commands.literal("intensity")
                            .then(Commands.argument("percent", IntegerArgumentType.integer(0, 100))
                                    .executes(PenumbraCommands::setIntensity)))
                    .then(Commands.literal("frequency")
                            .then(Commands.argument("percent", IntegerArgumentType.integer(0, 400))
                                    .executes(PenumbraCommands::setFrequency)))
                    .then(Commands.literal("jumpscarefrequency")
                            .then(Commands.argument("percent", IntegerArgumentType.integer(0, 200))
                                    .executes(PenumbraCommands::setJumpscareFrequency)))
                    .then(Commands.literal("reload")
                            .executes(PenumbraCommands::reloadConfig))
                    .then(Commands.literal("event")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests(EVENT_ID_SUGGESTIONS)
                                    .executes(PenumbraCommands::triggerEvent)
                                    .then(targetArgument(PenumbraCommands::triggerEvent)))
                            // ——— Phase 3 : sous-espaces nocturne et souterrain ———
                            .then(Commands.literal("night")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests(NIGHT_EVENT_SUGGESTIONS)
                                            .executes(PenumbraCommands::triggerNightEvent)
                                            .then(targetArgument(PenumbraCommands::triggerNightEvent))))
                            .then(Commands.literal("underground")
                                    .then(Commands.argument("id", StringArgumentType.word())
                                            .suggests(UNDERGROUND_EVENT_SUGGESTIONS)
                                            .executes(PenumbraCommands::triggerUndergroundEvent)
                                            .then(targetArgument(PenumbraCommands::triggerUndergroundEvent)))))
                    .then(Commands.literal("spawnhost")
                            .executes(PenumbraCommands::spawnHost)
                            .then(targetArgument(PenumbraCommands::spawnHost)))
                    .then(Commands.literal("originel")
                            .then(Commands.literal("test")
                                    .executes(PenumbraCommands::originelTest)
                                    .then(targetArgument(PenumbraCommands::originelTest))))
                    // ——— Phase 4 : Observateur ———
                    .then(Commands.literal("watcher")
                            .then(Commands.literal("test")
                                    .executes(PenumbraCommands::watcherTest)
                                    .then(targetArgument(PenumbraCommands::watcherTest))))
                    .then(Commands.literal("projectionist")
                            .then(Commands.literal("test")
                                    .executes(PenumbraCommands::projectionistTest)
                                    .then(targetArgument(PenumbraCommands::projectionistTest))))
                    .then(Commands.literal("rampant")
                            .then(Commands.literal("test")
                                    .executes(PenumbraCommands::rampantTest)
                                    .then(targetArgument(PenumbraCommands::rampantTest))))
                    .then(Commands.literal("absent")
                            .then(Commands.literal("test")
                                    .executes(PenumbraCommands::absentTest)
                                    .then(targetArgument(PenumbraCommands::absentTest))))
                    // ——— 0.15.0 : Faux-Semblant (créature vanilla possédée) ———
                    .then(Commands.literal("fauxmob")
                            .then(Commands.literal("test")
                                    .executes(context -> fauxMob(context, null)))
                            .then(Commands.literal("perfect")
                                    .executes(context -> fauxMob(context, FauxMobRules.Defect.NONE)))
                            .then(Commands.literal("silent")
                                    .executes(context -> fauxMob(context, FauxMobRules.Defect.SILENT)))
                            .then(Commands.literal("stare")
                                    .executes(context -> fauxMob(context, FauxMobRules.Defect.STARE)))
                            .then(Commands.literal("frozen")
                                    .executes(context -> fauxMob(context, FauxMobRules.Defect.FROZEN)))
                            .then(Commands.literal("status")
                                    .executes(PenumbraCommands::fauxMobStatus)))
                    .then(Commands.literal("perception")
                            .then(Commands.literal("companion")
                                    .executes(PenumbraCommands::perceptionCompanion)
                                    .then(targetArgument(PenumbraCommands::perceptionCompanion)))
                            .then(Commands.literal("status")
                                    .executes(PenumbraCommands::perceptionStatus)
                                    .then(targetArgument(PenumbraCommands::perceptionStatus)))
                            .then(Commands.literal("clear")
                                    .executes(PenumbraCommands::perceptionClear)
                                    .then(targetArgument(PenumbraCommands::perceptionClear))))
                    .then(Commands.literal("scuttler")
                            .then(Commands.literal("cross")
                                    .executes(PenumbraCommands::scuttlerCross)
                                    .then(targetArgument(PenumbraCommands::scuttlerCross)))
                            .then(Commands.literal("chase")
                                    .executes(PenumbraCommands::scuttlerChase)
                                    .then(targetArgument(PenumbraCommands::scuttlerChase))))
                    .then(Commands.literal("prop")
                            .then(Commands.argument("mesh", StringArgumentType.word())
                                    .suggests(PROP_MESH_SUGGESTIONS)
                                    .executes(PenumbraCommands::propPlace)
                                    .then(Commands.argument("scale", IntegerArgumentType.integer(10, 400))
                                            .executes(PenumbraCommands::propPlace))))
                    .then(Commands.literal("listener")
                            .then(Commands.literal("test")
                                    .executes(PenumbraCommands::listenerTest)
                                    .then(targetArgument(PenumbraCommands::listenerTest))))
                    .then(Commands.literal("profile")
                            .then(Commands.argument("profile", StringArgumentType.word())
                                    .suggests(PROFILE_SUGGESTIONS)
                                    .executes(PenumbraCommands::applyProfile)))
                    .then(Commands.literal("jumpscare")
                            .then(Commands.argument("type", StringArgumentType.word())
                                    .suggests(JUMPSCARE_SUGGESTIONS)
                                    .executes(PenumbraCommands::jumpscare)
                                    .then(targetArgument(PenumbraCommands::jumpscare))))
                    .then(Commands.literal("reset")
                            .executes(PenumbraCommands::reset)
                            .then(targetArgument(PenumbraCommands::reset)))
                    .then(Commands.literal("arc")
                            .then(Commands.literal("status")
                                    .executes(PenumbraCommands::arcStatus)
                                    .then(targetArgument(PenumbraCommands::arcStatus)))
                            .then(Commands.literal("start")
                                    .then(Commands.argument("theme", StringArgumentType.word())
                                            .suggests(ARC_THEME_SUGGESTIONS)
                                            .executes(PenumbraCommands::arcStart)
                                            .then(targetArgument(PenumbraCommands::arcStart))))
                            .then(Commands.literal("clear")
                                    .executes(PenumbraCommands::arcClear)
                                    .then(targetArgument(PenumbraCommands::arcClear))))
                    .then(Commands.literal("linked")
                            .then(Commands.argument("type", StringArgumentType.word())
                                    .suggests(LINKED_SCENE_SUGGESTIONS)
                                    .executes(PenumbraCommands::linkedScene)
                                    .then(targetArgument(PenumbraCommands::linkedScene))))
                    // ——— Phase 2 (SPEC2 §10) ———
                    .then(Commands.literal("chase")
                            .executes(PenumbraCommands::chase)
                            .then(targetArgument(PenumbraCommands::chase)))
                    .then(Commands.literal("scare")
                            .then(Commands.argument("id", StringArgumentType.word())
                                    .suggests(SCARE_SUGGESTIONS)
                                    .executes(PenumbraCommands::scare)
                                    .then(targetArgument(PenumbraCommands::scare))))
                    .then(Commands.literal("binoculars")
                            .then(Commands.literal("test")
                                    .executes(PenumbraCommands::binocularsTest))
                            .then(Commands.literal("host_sighting")
                                    .executes(PenumbraCommands::binocularsHostSighting)
                                    .then(targetArgument(PenumbraCommands::binocularsHostSighting)))
                            .then(Commands.literal("reaction")
                                    .then(Commands.argument("type", StringArgumentType.word())
                                            .suggests(REACTION_SUGGESTIONS)
                                            .executes(PenumbraCommands::binocularsReaction)
                                            .then(targetArgument(PenumbraCommands::binocularsReaction))))
                            .then(Commands.literal("reset")
                                    .executes(PenumbraCommands::binocularsReset)
                                    .then(targetArgument(PenumbraCommands::binocularsReset)))
                            // ——— Phase 3 : repère structurel via les jumelles ———
                            .then(Commands.literal("landmark")
                                    .executes(PenumbraCommands::binocularsLandmark)
                                    .then(targetArgument(PenumbraCommands::binocularsLandmark))))
                    // ——— Phase 3 : maintenance ———
                    .then(Commands.literal("debug")
                            .requires(source -> source.permissions()
                                    .hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                            .then(Commands.literal("restoration")
                                    .executes(PenumbraCommands::debugRestoration)
                                    .then(targetArgument(PenumbraCommands::debugRestoration))));
            // ——— Phase 3 : sous-arbres livrés par leurs modules ———
            StoryCommands.attach(root);
            StructureCommands.attach(root);
            ItemCommands.attach(root);
            CopyCommands.attach(root);
            dispatcher.register(root);
        });
    }

    /** Argument final optionnel commun aux commandes de test multijoueur. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?>
    targetArgument(com.mojang.brigadier.Command<CommandSourceStack> command) {
        return Commands.argument(TARGET_ARGUMENT, EntityArgument.player()).executes(command);
    }

    /** Cible explicite si fournie, sinon joueur qui exécute la commande. */
    private static ServerPlayer commandPlayer(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        try {
            return EntityArgument.getPlayer(context, TARGET_ARGUMENT);
        } catch (IllegalArgumentException missingOptionalTarget) {
            return context.getSource().getPlayerOrException();
        }
    }

    /** /penumbra status : état, tension, dernier événement et stade de l'histoire. */
    private static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        PenumbraPersistentState data = PenumbraPersistentState.get(source.getServer());
        PlayerHorrorProfile profile = data.profile(player.getUUID());
        TensionState state = HorrorDirector.getState(source.getServer(), player.getUUID());
        String lastEvent = profile.lastEventId() != null ? profile.lastEventId() : "aucun";
        source.sendSystemMessage(Component.translatable("command.penumbra.status",
                String.format(Locale.ROOT, "%.1f", profile.tension()),
                state.name(),
                lastEvent,
                StoryManager.getStage(player)));
        return 1;
    }

    /** /penumbra tension <0-100> : fixe la tension du joueur exécutant. */
    private static int setTension(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        int value = IntegerArgumentType.getInteger(context, "value");
        HorrorDirector.setTension(player, value);
        source.sendSystemMessage(Component.translatable("command.penumbra.tension_set", value));
        return 1;
    }

    /** /penumbra intensity <0-100> : curseur maître, 50 = normal et 100 = maximum. */
    private static int setIntensity(CommandContext<CommandSourceStack> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        PenumbraConfig.get().horrorIntensity = percent / 50.0f;
        PenumbraConfig.get().eventFrequency = percent / 25.0f;
        PenumbraConfig.get().jumpscareFrequency = percent / 50.0f;
        PenumbraConfig.save();
        context.getSource().sendSystemMessage(
                Component.translatable("command.penumbra.intensity_set", percent));
        return 1;
    }

    private static int arcStatus(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = commandPlayer(context);
        DirectorMemory.ArcState arc = HorrorDirector.getArcState(player);
        long remaining = Math.max(0L,
                arc.phaseEndsTick() - player.level().getGameTime()) / 20L;
        context.getSource().sendSystemMessage(Component.translatable(
                "command.penumbra.arc.status", arc.theme().name(), arc.phase().name(),
                remaining, arc.beatCount()));
        return 1;
    }

    private static int arcStart(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = commandPlayer(context);
        String raw = StringArgumentType.getString(context, "theme");
        DirectorMemory.ArcTheme theme;
        try {
            theme = DirectorMemory.ArcTheme.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFailure(Component.translatable(
                    "command.penumbra.arc.unknown", raw));
            return 0;
        }
        if (theme == DirectorMemory.ArcTheme.NONE) {
            return 0;
        }
        HorrorDirector.forceArc(player, theme);
        context.getSource().sendSystemMessage(Component.translatable(
                "command.penumbra.arc.started", theme.name(),
                player.getGameProfile().name()));
        return 1;
    }

    private static int arcClear(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = commandPlayer(context);
        HorrorDirector.clearArc(player);
        context.getSource().sendSystemMessage(Component.translatable(
                "command.penumbra.arc.cleared", player.getGameProfile().name()));
        return 1;
    }

    private static int linkedScene(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        ServerPlayer player = commandPlayer(context);
        String raw = StringArgumentType.getString(context, "type");
        LinkedSceneType type = LinkedSceneType.byId(raw);
        if (type == null) {
            context.getSource().sendFailure(Component.translatable(
                    "command.penumbra.linked.unknown", raw));
            return 0;
        }
        if (!LinkedSceneManager.forceStart(player, type)) {
            context.getSource().sendFailure(Component.translatable(
                    "command.penumbra.linked.failed", raw));
            return 0;
        }
        context.getSource().sendSystemMessage(Component.translatable(
                "command.penumbra.linked.started", raw,
                player.getGameProfile().name()));
        return 1;
    }

    /** /penumbra frequency <0-400> : 100 = fréquence normale, 400 = maximum. */
    private static int setFrequency(CommandContext<CommandSourceStack> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        PenumbraConfig.get().eventFrequency = percent / 100.0f;
        PenumbraConfig.save();
        context.getSource().sendSystemMessage(
                Component.translatable("command.penumbra.frequency_set", percent));
        return 1;
    }

    /** /penumbra jumpscarefrequency <0-200> : fréquence serveur des jumpscares. */
    private static int setJumpscareFrequency(CommandContext<CommandSourceStack> context) {
        int percent = IntegerArgumentType.getInteger(context, "percent");
        PenumbraConfig.get().jumpscareFrequency = percent / 100.0f;
        PenumbraConfig.save();
        context.getSource().sendSystemMessage(
                Component.translatable("command.penumbra.jumpscare_frequency_set", percent));
        return 1;
    }

    /** Recharge immédiatement config/penumbra.json sans redémarrer le serveur. */
    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        PenumbraConfig.load();
        HorrorDirector.cancelDelayedScaresIfDisabled();
        context.getSource().sendSystemMessage(
                Component.translatable("command.penumbra.config_reloaded"));
        return 1;
    }

    /** /penumbra event <id> : force un événement (bypass grâce et cooldowns). */
    private static int triggerEvent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String id = StringArgumentType.getString(context, "id");
        if (HorrorDirector.forceEvent(player, id)) {
            source.sendSystemMessage(Component.translatable("command.penumbra.event_triggered", id));
            return 1;
        }
        source.sendSystemMessage(Component.translatable("command.penumbra.event_unknown", id));
        return 0;
    }

    /**
     * /penumbra event night <id> : force un événement nocturne (Phase 3). Même
     * mécanisme que {@link #triggerEvent} ({@code HorrorDirector.forceEvent},
     * donc bypass grâce/cooldowns mais respect de {@code canRun}) ; l'id est
     * d'abord validé contre {@code NightEvents.byId} pour rester dans
     * l'espace nocturne.
     */
    private static int triggerNightEvent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String id = StringArgumentType.getString(context, "id");
        if (NightEvents.byId(id) != null && HorrorDirector.forceEvent(player, id)) {
            source.sendSystemMessage(Component.translatable("command.penumbra.event_triggered", id));
            return 1;
        }
        source.sendSystemMessage(Component.translatable("command.penumbra.event_unknown", id));
        return 0;
    }

    /**
     * /penumbra event underground <id> : force un événement souterrain
     * (Phase 3), même mécanisme que {@link #triggerNightEvent} avec validation
     * de l'id contre {@code UndergroundEvents.byId}.
     */
    private static int triggerUndergroundEvent(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String id = StringArgumentType.getString(context, "id");
        if (UndergroundEvents.byId(id) != null && HorrorDirector.forceEvent(player, id)) {
            source.sendSystemMessage(Component.translatable("command.penumbra.event_triggered", id));
            return 1;
        }
        source.sendSystemMessage(Component.translatable("command.penumbra.event_unknown", id));
        return 0;
    }

    /** /penumbra spawnhost : fait apparaître l'Hôte à distance modérée du joueur. */
    private static int spawnHost(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        HostEntity host = HostEntity.spawnNear(player.level(), player, 12.0, 24.0);
        if (host != null) {
            source.sendSystemMessage(Component.translatable("command.penumbra.host_spawned"));
            return 1;
        }
        source.sendSystemMessage(Component.translatable("command.penumbra.host_spawn_failed"));
        return 0;
    }

    /**
     * /penumbra watcher test (Phase 4) : fait apparaître un Observateur à 15–30
     * blocs dans le cône de regard du joueur (±120° autour de son yaw), via
     * {@code WatcherEntity.spawnInGazeCone}. Échoue proprement si aucun
     * emplacement valide n'est trouvé.
     */
    private static int watcherTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        WatcherEntity watcher = WatcherEntity.spawnInGazeCone(
                player.level(), player, 15.0, 30.0, player.getYRot(), 120.0f);
        if (watcher != null) {
            source.sendSystemMessage(Component.translatable("command.penumbra.watcher_spawned"));
            return 1;
        }
        source.sendSystemMessage(Component.translatable("command.penumbra.watcher_spawn_failed"));
        return 0;
    }

    /** /penumbra originel test : apparition personnelle adaptée à la surface/grotte. */
    private static int originelTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (OriginelManager.forceSighting(player)) {
            source.sendSystemMessage(Component.translatable("command.penumbra.originel_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable("command.penumbra.originel_spawn_failed"));
        return 0;
    }

    /** /penumbra projectionist test : manifestation physique derrière le joueur. */
    private static int projectionistTest(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (ProjectionistEntity.spawnBehind(player.level(), player) != null) {
            source.sendSystemMessage(
                    Component.translatable("command.penumbra.projectionist_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable(
                "command.penumbra.projectionist_spawn_failed"));
        return 0;
    }

    /** /penumbra rampant test : cherche une vraie surface sombre autour du joueur. */
    /** {@code defect == null} : tirage naturel complet (déguisement, défaut, parfaits). */
    private static int fauxMob(CommandContext<CommandSourceStack> context,
                               FauxMobRules.Defect defect) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        boolean spawned = defect == null
                ? FauxMobManager.spawnNatural(player)
                : FauxMobManager.spawnWithDefect(player, defect);
        if (spawned) {
            source.sendSystemMessage(Component.translatable("command.penumbra.fauxmob_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable("command.penumbra.fauxmob_failed"));
        return 0;
    }

    private static int fauxMobStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int active = FauxMobManager.activeSessions(source.getServer());
        source.sendSystemMessage(
                Component.translatable("command.penumbra.fauxmob_status", active));
        return active;
    }

    private static int rampantTest(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (RampantManager.forceSpawn(player) != null) {
            source.sendSystemMessage(Component.translatable("command.penumbra.rampant_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable("command.penumbra.rampant_spawn_failed"));
        return 0;
    }

    /** /penumbra absent test : apparition leurre à distance ou près d'un refuge. */
    private static int absentTest(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (AbsentManager.forceSighting(player)) {
            source.sendSystemMessage(Component.translatable("command.penumbra.absent_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable("command.penumbra.absent_spawn_failed"));
        return 0;
    }

    /** /penumbra perception companion : force un faux compagnon strictement privé. */
    private static int perceptionCompanion(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String name = player.getGameProfile().name();
        if (IndividualPerceptionManager.startFalseCompanion(player, true)) {
            source.sendSystemMessage(Component.translatable(
                    "command.penumbra.perception.started", name));
            return 1;
        }
        source.sendFailure(Component.translatable(
                "command.penumbra.perception.failed", name));
        return 0;
    }

    /** /penumbra perception status : identité copiée et durée transitoire restante. */
    private static int perceptionStatus(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        var active = IndividualPerceptionManager.active(player);
        if (active.isEmpty()) {
            source.sendSystemMessage(Component.translatable(
                    "command.penumbra.perception.none", player.getGameProfile().name()));
            return 0;
        }
        IndividualPerceptionManager.Snapshot snapshot = active.orElseThrow();
        source.sendSystemMessage(Component.translatable(
                "command.penumbra.perception.status",
                snapshot.copiedName(), snapshot.remainingSeconds()));
        return 1;
    }

    /** /penumbra perception clear : dissipe uniquement la scène de la cible. */
    private static int perceptionClear(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String name = player.getGameProfile().name();
        if (IndividualPerceptionManager.clear(player)) {
            source.sendSystemMessage(Component.translatable(
                    "command.penumbra.perception.cleared", name));
            return 1;
        }
        source.sendSystemMessage(Component.translatable(
                "command.penumbra.perception.none", name));
        return 0;
    }

    /** /penumbra scuttler cross : force une traversée rapide devant le joueur. */
    /**
     * /penumbra pyre : tout ce qu'il faut savoir sur le Bûcher de ce monde.
     *
     * <p>Existe parce que « je ne vois pas la fumée » a trois causes possibles
     * — trop loin, distance d'affichage trop courte, ou site immergé — et
     * qu'aucune ne se distingue des autres en jeu. Cette commande les sépare.</p>
     */
    private static int pyreStatus(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (!(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable("command.penumbra.pyre_wrong_dimension"));
            return 0;
        }

        BlockPos site = PyreManager.resolvedSite(level);
        double distance = PyreManager.distanceTo(player, site);
        double range = PyreManager.plumeRangeFor(player);

        source.sendSystemMessage(Component.translatable("command.penumbra.pyre_site",
                site.getX(), site.getZ(),
                String.format(Locale.ROOT, "%.0f", distance),
                compass(site.getX() - player.getX(), site.getZ() - player.getZ())));
        source.sendSystemMessage(Component.translatable("command.penumbra.pyre_range",
                String.format(Locale.ROOT, "%.0f", range),
                player.requestedViewDistance(),
                distance <= range
                        ? Component.translatable("command.penumbra.pyre_visible")
                        : Component.translatable("command.penumbra.pyre_too_far",
                                String.format(Locale.ROOT, "%.0f", distance - range))));
        source.sendSystemMessage(Component.translatable("command.penumbra.pyre_built",
                PyreManager.isBuilt(source.getServer())
                        ? Component.translatable("command.penumbra.yes")
                        : Component.translatable("command.penumbra.no")));

        // Les trois causes qui empêchent le camp de sortir de terre, toutes
        // invisibles en jeu : chunk non chargé, site en mer, option coupée.
        boolean loaded = level.getChunkSource()
                .getChunkNow(site.getX() >> 4, site.getZ() >> 4) != null;
        int surface = loaded
                ? level.getHeight(Heightmap.Types.WORLD_SURFACE, site.getX(), site.getZ())
                : -1;
        source.sendSystemMessage(Component.translatable("command.penumbra.pyre_ground",
                yesNo(loaded),
                loaded ? String.valueOf(surface) : "?",
                yesNo(loaded && surface > level.getSeaLevel()),
                yesNo(PenumbraConfig.get().signalPyreEnabled
                        && PenumbraConfig.get().journalEnabled)));
        return 1;
    }

    /** /penumbra pyre build : construit le camp maintenant, sans attendre. */
    private static int pyreBuild(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (!(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable("command.penumbra.pyre_wrong_dimension"));
            return 0;
        }
        BlockPos site = PyreManager.resolvedSite(level);
        PyreManager.Result result = PyreManager.build(source.getServer(), level, site);
        source.sendSystemMessage(Component.translatable(
                "command.penumbra.pyre_result_" + result.name().toLowerCase(Locale.ROOT),
                site.getX(), site.getZ()));
        return result == PyreManager.Result.BUILT ? 1 : 0;
    }

    /**
     * /penumbra pyre tp : se poser sur le site, puis construire s'il le faut.
     *
     * <p>Le site est à onze cents ou deux mille six cents blocs : y aller à pied
     * pour tester une seule chose n'a aucun sens. La commande <b>charge le chunk
     * de force</b> avant de calculer l'altitude — sans ça on se téléporterait
     * dans le vide, ou dans la pierre, puisque le terrain n'existe pas encore.
     * C'est un chargement bloquant, ce qui serait inacceptable dans une boucle
     * de jeu mais ne l'est pas dans une commande d'administration.</p>
     *
     * <p>La construction est tentée dans la foulée : arriver sur un site vide
     * était précisément le symptôme qui a motivé cette commande.</p>
     */
    private static int pyreTeleport(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (!(player.level() instanceof ServerLevel level)
                || level.dimension() != Level.OVERWORLD) {
            source.sendFailure(Component.translatable("command.penumbra.pyre_wrong_dimension"));
            return 0;
        }
        BlockPos site = PyreManager.resolvedSite(level);

        // Chargement bloquant : sans lui, getHeight rendrait une valeur du vide.
        level.getChunk(site.getX() >> 4, site.getZ() >> 4);
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                site.getX(), site.getZ());

        player.teleportTo(level, site.getX() + 0.5, surface, site.getZ() + 0.5,
                java.util.Set.of(), player.getYRot(), player.getXRot(), true);
        // Le premier essai réel s'est soldé par « AlessioP s'est écrasé au sol » :
        // la chute accumulée avant la téléportation reste comptée à l'arrivée.
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        source.sendSystemMessage(Component.translatable("command.penumbra.pyre_teleported",
                site.getX(), surface, site.getZ()));

        PyreManager.Result result = PyreManager.build(source.getServer(), level, site);
        source.sendSystemMessage(Component.translatable(
                "command.penumbra.pyre_result_" + result.name().toLowerCase(Locale.ROOT),
                site.getX(), site.getZ()));
        return 1;
    }

    /** /penumbra pyre reset : oublie la construction, pour retester. */
    private static int pyreReset(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        PyreManager.reset(source.getServer());
        PyreManager.forget();
        source.sendSystemMessage(Component.translatable("command.penumbra.pyre_reset"));
        return 1;
    }

    /** Point cardinal approximatif d'un décalage horizontal. */
    private static String compass(double dx, double dz) {
        String vertical = dz < -24.0 ? "nord" : dz > 24.0 ? "sud" : "";
        String horizontal = dx > 24.0 ? "est" : dx < -24.0 ? "ouest" : "";
        String joined = vertical.isEmpty() || horizontal.isEmpty()
                ? vertical + horizontal
                : vertical + "-" + horizontal;
        return joined.isEmpty() ? "sur place" : joined;
    }

    /**
     * /penumbra diag : pourquoi rien n'apparaît.
     *
     * <p>Les manifestations automatiques passent une dizaine de portes — grâce,
     * stade narratif, tension, lumière, heure, quotas. Quand rien ne se produit,
     * c'est qu'une de ces portes est fermée, et rien en jeu ne dit laquelle.
     * Cette commande les affiche toutes d'un coup.</p>
     */
    private static int diagnose(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        PenumbraConfig config = PenumbraConfig.get();
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = player.blockPosition();

        boolean grace = HorrorDirector.isGraceComplete(player);
        int stage = HorrorDirector.effectiveContentStage(player);
        boolean manifestations = HorrorDirector.physicalManifestationAllowed(player);
        boolean sceneFree = HorrorDirector.automaticSceneAvailable(player);
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        boolean seesSky = level.canSeeSky(pos.above());
        boolean night = level.getOverworldClockTime() % 24000L >= 13000L
                && level.getOverworldClockTime() % 24000L <= 23000L;
        boolean peaceful = level.getDifficulty() == Difficulty.PEACEFUL;

        source.sendSystemMessage(Component.translatable("command.penumbra.diag_header",
                player.getGameProfile().name()));
        source.sendSystemMessage(Component.translatable("command.penumbra.diag_gates",
                yesNo(grace), stage, yesNo(manifestations), yesNo(sceneFree)));
        source.sendSystemMessage(Component.translatable("command.penumbra.diag_place",
                blockLight, yesNo(seesSky), yesNo(night), yesNo(!peaceful)));
        source.sendSystemMessage(Component.translatable("command.penumbra.diag_identities",
                KnownIdentities.get(source.getServer()).size(),
                KnownIdentities.stranger(source.getServer()).name()));

        // Verdict Cavaleuse : la créature dont les conditions sont les plus
        // strictes, et donc celle qui manque le plus souvent à l'appel.
        List<String> blocking = new ArrayList<>();
        if (peaceful) {
            blocking.add("difficulté paisible");
        }
        if (!grace) {
            blocking.add("période de grâce (" + config.eventGracePeriodSeconds + " s)");
        }
        if (stage < 1) {
            blocking.add("stade de contenu 0, il en faut 1");
        }
        if (blockLight > 6) {
            blocking.add("lumière de bloc " + blockLight + ", il en faut 6 ou moins");
        }
        if (seesSky && !night) {
            blocking.add("il fait jour à ciel ouvert");
        }
        if (!seesSky && pos.getY() > level.getSeaLevel() + 20) {
            blocking.add("sous plafond mais trop haut pour compter comme grotte");
        }
        if (!manifestations) {
            blocking.add("manifestations physiques suspendues");
        }
        source.sendSystemMessage(blocking.isEmpty()
                ? Component.translatable("command.penumbra.diag_ready")
                : Component.translatable("command.penumbra.diag_blocked",
                        String.join(", ", blocking)));
        return 1;
    }

    private static Component yesNo(boolean value) {
        return Component.translatable(value
                ? "command.penumbra.yes" : "command.penumbra.no");
    }

    /**
     * /penumbra prop &lt;maillage&gt; [échelle] : pose un accessoire de décor
     * portant un maillage {@code .obj}, devant le joueur, tourné vers lui.
     * L'échelle est en pourcentage (100 = taille du fichier).
     *
     * <p>Le serveur ne vérifie pas que le fichier existe : les maillages vivent
     * dans les ressources <b>client</b>, et un pack peut en ajouter. Un nom
     * inconnu produit donc un accessoire invisible, pas une erreur.</p>
     */
    private static int propPlace(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        String mesh = StringArgumentType.getString(context, "mesh");
        float scale = 1.0f;
        try {
            scale = IntegerArgumentType.getInteger(context, "scale") / 100.0f;
        } catch (IllegalArgumentException noScale) {
            // Argument optionnel absent : on garde l'échelle du fichier.
        }
        // Deux blocs devant le regard, posé au sol, face au joueur.
        Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
        look = look.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
        Vec3 position = player.position().add(look.scale(2.0));
        MeshPropEntity prop = MeshPropEntity.place(level, position,
                player.getYRot() + 180.0f, mesh, "", scale);
        if (prop == null) {
            source.sendFailure(Component.translatable("command.penumbra.prop_failed"));
            return 0;
        }
        source.sendSystemMessage(Component.translatable(
                "command.penumbra.prop_placed", mesh));
        return 1;
    }

    /**
     * /penumbra listener test : apparition réelle de L'Écouteur, sans chance ni
     * cooldown. Le placement, l'obscurité et les collisions restent vérifiés.
     */
    private static int listenerTest(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (ListenerManager.force(commandPlayer(context))) {
            source.sendSystemMessage(Component.translatable(
                    "command.penumbra.listener_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable("command.penumbra.listener_failed"));
        return 0;
    }

    /**
     * /penumbra profile &lt;gentle|standard|nightmare&gt; : applique un préréglage
     * d'accessibilité puis sauvegarde. Les réglages fins non cités par le profil
     * sont conservés tels quels.
     */
    private static int applyProfile(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String profile = StringArgumentType.getString(context, "profile")
                .toLowerCase(Locale.ROOT);
        if (!PenumbraConfig.get().applyProfile(profile)) {
            source.sendFailure(Component.translatable(
                    "command.penumbra.profile_unknown", profile));
            return 0;
        }
        PenumbraConfig.save();
        source.sendSystemMessage(Component.translatable(
                "command.penumbra.profile_applied", profile));
        return 1;
    }

    private static int scuttlerCross(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (ScuttlerManager.forceCrossing(commandPlayer(context))) {
            source.sendSystemMessage(Component.translatable(
                    "command.penumbra.scuttler_cross_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable("command.penumbra.scuttler_spawn_failed"));
        return 0;
    }

    /** /penumbra scuttler chase : force la poursuite inoffensive de 3–5 s. */
    private static int scuttlerChase(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (ScuttlerManager.forceChase(commandPlayer(context))) {
            source.sendSystemMessage(Component.translatable(
                    "command.penumbra.scuttler_chase_spawned"));
            return 1;
        }
        source.sendFailure(Component.translatable("command.penumbra.scuttler_spawn_failed"));
        return 0;
    }

    /** /penumbra jumpscare <type> : déclenche un jumpscare côté client du joueur. */
    private static int jumpscare(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String type = StringArgumentType.getString(context, "type");
        if (!JUMPSCARE_TYPES.contains(type)) {
            source.sendFailure(Component.literal("Type de jumpscare inconnu : " + type
                    + " (attendu : " + String.join(", ", JUMPSCARE_TYPES) + ")"));
            return 0;
        }
        if (PenumbraConfig.get().jumpscareFrequency <= 0.0f) {
            source.sendFailure(Component.translatable("command.penumbra.jumpscare_disabled"));
            return 0;
        }
        PenumbraNetworking.sendJumpscare(player, type, 1.0f);
        source.sendSystemMessage(Component.translatable("command.penumbra.jumpscare_triggered", type));
        return 1;
    }

    /** /penumbra reset : profil neuf, nouvelle grâce et histoire au stade 0. */
    private static int reset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        HorrorDirector.reset(player);
        source.sendSystemMessage(Component.translatable("command.penumbra.reset_done"));
        return 1;
    }

    // ---------------------------------------------------------------------
    // Phase 2 (SPEC2 §10)
    // ---------------------------------------------------------------------

    /**
     * /penumbra chase : démarre une poursuite. Réutilise l'Hôte existant le
     * plus proche à moins de {@value #CHASE_HOST_SEARCH_RADIUS} blocs, sinon en
     * fait apparaître un via {@code HostEntity.spawnNear(level, player, 16, 24)}.
     * Commande de débogage : force la poursuite même hors conditions naturelles
     * (comme {@code spawnhost} en Phase 1).
     */
    private static int chase(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        ServerLevel level = player.level();
        HostEntity host = findNearestHost(level, player, CHASE_HOST_SEARCH_RADIUS);
        if (host == null) {
            host = HostEntity.spawnNear(level, player, 16.0, 24.0);
        }
        if (host == null) {
            source.sendFailure(Component.translatable("command.penumbra.host_spawn_failed"));
            return 0;
        }
        if (!HorrorDirector.startChase(player, host)) {
            source.sendFailure(Component.translatable("command.penumbra.host_spawn_failed"));
            return 0;
        }
        source.sendSystemMessage(Component.translatable("command.penumbra.chase_started"));
        return 1;
    }

    /** /penumbra scare <id> : force un jumpscare retardé du directeur (SPEC2 §6). */
    private static int scare(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String id = StringArgumentType.getString(context, "id");
        if (!SCARE_IDS.contains(id)) {
            source.sendFailure(Component.literal("Scare inconnu : " + id
                    + " (attendu : " + String.join(", ", SCARE_IDS) + ")"));
            return 0;
        }
        if (HorrorDirector.forceScare(player, id)) {
            source.sendSystemMessage(Component.translatable("command.penumbra.scare_triggered", id));
            return 1;
        }
        source.sendFailure(Component.literal("Scare '" + id + "' non déclenché "
                + "(scène active, écran ouvert, option désactivée ou aucun emplacement praticable)."));
        return 0;
    }

    /**
     * /penumbra binoculars test : force un zoom de 3 s ({@value #DEBUG_ZOOM_TICKS}
     * ticks) sur le client du joueur via {@code BinocularsManager.debugZoom}.
     * <p>Le manager est une classe client : l'appel n'est possible que depuis le
     * client intégré (solo / hôte LAN). Aucun paquet réseau dédié n'existe dans
     * le contrat Phase 2 pour ce test, et PenumbraNetworking est hors scope ;
     * l'appel local au client intégré est la solution retenue (voir
     * {@link #tryIntegratedClientZoom(UUID)}). Sur serveur dédié, la commande
     * échoue proprement avec un message explicite.</p>
     */
    private static int binocularsTest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            source.sendFailure(Component.literal("Zoom de test impossible : serveur dédié "
                    + "(aucun client intégré). À tester en solo."));
            return 0;
        }
        if (!tryIntegratedClientZoom(player.getUUID())) {
            source.sendFailure(Component.literal("Zoom de test impossible : le joueur n'est pas "
                    + "le client hôte de cette partie (réservé au solo / hôte LAN)."));
            return 0;
        }
        source.sendSystemMessage(Component.translatable("command.penumbra.binoculars.test_done"));
        return 1;
    }

    /**
     * /penumbra binoculars host_sighting : force l'événement
     * {@code binoculars_sighting} (apparition lointaine dans le cône de regard).
     * Échoue si les conditions de l'événement ne sont pas réunies (zoom inactif,
     * tension &lt; WATCHED, host/binocularsHostReactions désactivés, Hôte déjà proche).
     */
    private static int binocularsHostSighting(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        if (HorrorDirector.forceEvent(player, "binoculars_sighting")) {
            source.sendSystemMessage(Component.translatable("command.penumbra.binoculars.sighting_done"));
            return 1;
        }
        source.sendFailure(Component.literal("Événement binoculars_sighting impossible : zoom inactif, "
                + "tension < WATCHED, hostEnabled/binocularsHostReactionsEnabled=false, "
                + "ou Hôte déjà présent à moins de 100 blocs."));
        return 0;
    }

    /**
     * /penumbra binoculars reaction <type> : force une réaction de l'Hôte aux
     * jumelles via la surcharge {@code HorrorDirector.tryBinocularReaction(ServerPlayer,
     * HostEntity, String)} (SPEC2 §8). Cible : l'Hôte le plus proche à moins de
     * {@value #REACTION_HOST_SEARCH_RADIUS} blocs ; à défaut, un Hôte est
     * invoqué à 24–40 blocs (comme distant_sighting) pour rendre le test possible.
     */
    private static int binocularsReaction(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        String type = StringArgumentType.getString(context, "type");
        if (!BINOCULAR_REACTIONS.contains(type)) {
            source.sendFailure(Component.translatable("command.penumbra.binoculars.reaction_unknown", type));
            return 0;
        }
        ServerLevel level = player.level();
        HostEntity host = findNearestHost(level, player, REACTION_HOST_SEARCH_RADIUS);
        if (host == null) {
            host = HostEntity.spawnNear(level, player, 24.0, 40.0);
        }
        if (host == null) {
            source.sendFailure(Component.translatable("command.penumbra.host_spawn_failed"));
            return 0;
        }
        HorrorDirector.tryBinocularReaction(player, host, type);
        source.sendSystemMessage(Component.translatable("command.penumbra.binoculars.reaction_done", type));
        return 1;
    }

    /**
     * /penumbra binoculars reset : réinitialise l'état jumelles du joueur côté
     * serveur (indicateurs zoom/inventaire suivis par {@code PenumbraC2S}).
     * L'état visuel client se rétablit de lui-même au prochain événement de
     * touche ou d'écran (SPEC2 §8) ; aucun appel client supplémentaire n'est
     * requis par le contrat.
     */
    private static int binocularsReset(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        PenumbraC2S.clear(player.getUUID());
        source.sendSystemMessage(Component.translatable("command.penumbra.binoculars.reset_done"));
        return 1;
    }

    // ---------------------------------------------------------------------
    // Phase 3
    // ---------------------------------------------------------------------

    /**
     * /penumbra binoculars landmark : décrit la structure Penumbra la plus
     * proche (direction cardinale + distance énigmatique, composant traduit
     * côté client par {@code StructureCommands.describeNearest}). Message système discret,
     * dans le style des notes narratives.
     */
    private static int binocularsLandmark(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        source.sendSystemMessage(StructureCommands.describeNearest(player).copy()
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        return 1;
    }

    /**
     * /penumbra debug restoration (permission 2) : restaure d'un coup tous les
     * blocs modifiés par les événements souterrains et toutes les copies
     * imparfaites du niveau du joueur. Les deux APIs ne retournent rien :
     * le feedback reste un simple message de confirmation.
     */
    private static int debugRestoration(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = commandPlayer(context);
        ServerLevel level = player.level();
        UndergroundRestorationTracker.restoreAll(level);
        CopyManager.restoreAll(level);
        source.sendSystemMessage(Component.translatable("command.penumbra.debug.restoration_done"));
        return 1;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Hôte vivant le plus proche du joueur dans le rayon donné, ou null.
     * 26.1 : pas de {@code getEntitiesOfClass} ; on utilise
     * {@code Level.getEntities(EntityTypeTest, AABB, Predicate)} avec
     * {@code EntityTypeTest.forClass} (vérifié dans les sources 26.1.2).
     */
    private static HostEntity findNearestHost(ServerLevel level, ServerPlayer player, double radius) {
        List<HostEntity> hosts = level.getEntities(
                EntityTypeTest.forClass(HostEntity.class),
                player.getBoundingBox().inflate(radius),
                host -> host.isAlive() && host.isOwnedBy(player));
        HostEntity nearest = null;
        double bestDistanceSq = radius * radius;
        for (HostEntity host : hosts) {
            double distanceSq = host.distanceToSqr(player);
            if (distanceSq <= bestDistanceSq) {
                bestDistanceSq = distanceSq;
                nearest = host;
            }
        }
        return nearest;
    }

    /**
     * Exécute {@code BinocularsManager.debugZoom} sur le client intégré si et
     * seulement si le joueur de la commande est le joueur de ce client
     * (protection LAN : ne jamais zoomer l'écran d'un autre joueur).
     * <p>Méthode confinée côté client : elle n'est appelée que lorsque
     * {@code FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT},
     * donc jamais chargée sur un serveur dédié. Le basculement sur le thread
     * client passe par {@code Minecraft.execute(Runnable)} (vérifié :
     * {@code BlockableEventLoop.execute} dans les sources 26.1.2).</p>
     *
     * @return true si le zoom de test a été planifié sur le client du joueur
     */
    @Environment(EnvType.CLIENT)
    private static boolean tryIntegratedClientZoom(UUID playerId) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !client.player.getUUID().equals(playerId)) {
            return false;
        }
        client.execute(() -> BinocularsManager.debugZoom(DEBUG_ZOOM_TICKS));
        return true;
    }
}
