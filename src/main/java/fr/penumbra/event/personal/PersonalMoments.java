package fr.penumbra.event.personal;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.copy.CopyTracker;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.photo.FauxPhotoPainter;
import fr.penumbra.photo.PhotoAlbumState;
import fr.penumbra.photo.PhotoContent;
import fr.penumbra.photo.PhotoData;
import fr.penumbra.registry.PenumbraSounds;
import fr.penumbra.story.StoryManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Moments personnels et one-shots (0.6.0) — gérés hors du pool du directeur.
 * <ul>
 *   <li><b>Écho de coffre</b> (au réveil) : un coffre proche semble avoir
 *       bougé, sans jamais lire ni réordonner son inventaire ;</li>
 *   <li><b>One-shots par monde</b> (persistés dans l'état du monde, une seule
 *       fois par joueur) : {@code bed_shift} — quelque chose bouge sous le
 *       lit sans casser le point de réapparition ; {@code animal_gaze} — tous les
 *       animaux du rayon fixent le joueur simultanément 3 s ;
 *       {@code fake_join} — en solo uniquement ({@code metaMessagesEnabled}),
 *       un faux message vanilla « X a rejoint la partie » avec un pseudo à
 *       une lettre du sien, suivi du départ 60–120 s plus tard. Les messages
 *       utilisent les clés vanilla {@code multiplayer.player.joined/left} :
 *       rendus dans la langue du client (français inclus) ;</li>
 *   <li><b>Journal incomplet</b> : au clic droit, des lignes générées depuis
 *       les VRAIES statistiques du profil — le narrateur, c'est l'Hôte.</li>
 * </ul>
 */
public final class PersonalMoments {

    /** Cadence des décisions one-shot (10 s). */
    private static final int CHECK_INTERVAL_TICKS = 200;
    /** Chance de retri d'un coffre au réveil. */
    private static final float CHEST_SHUFFLE_CHANCE = 0.18f;
    /** Intervalle minimal entre deux retris pour un joueur (1 jour de jeu). */
    private static final long CHEST_SHUFFLE_COOLDOWN_TICKS = 24000L;
    /** Chance du one-shot bed_shift à un réveil donné. */
    private static final float BED_SHIFT_CHANCE = 0.10f;
    /** Chance du one-shot animal_gaze par joueur et par échéance. */
    private static final int ANIMAL_GAZE_ONE_IN = 400;
    /** Chance du one-shot fake_join par joueur et par échéance (solo). */
    private static final int FAKE_JOIN_ONE_IN = 500;
    /** Rayon du regard simultané des animaux. */
    private static final double ANIMAL_GAZE_RADIUS = 20.0;
    /** Nombre maximal d'animaux figés (coût borné). */
    private static final int ANIMAL_GAZE_MAX = 12;
    /** Durée du regard simultané (3 s). */
    private static final int ANIMAL_GAZE_TICKS = 60;
    /** Anti-spam du journal (5 s). */
    private static final long JOURNAL_COOLDOWN_TICKS = 100L;
    // ——— 0.16.0 : Les Échos ———
    /** Chance du one-shot sleep_photo à un réveil donné (stade ≥ 4). */
    private static final float SLEEP_PHOTO_CHANCE = 0.08f;
    private static final int SLEEP_PHOTO_MIN_STAGE = 4;
    /** Chance du one-shot intruder_photo par joueur et par échéance (stade ≥ 5). */
    private static final int INTRUDER_PHOTO_ONE_IN = 600;
    private static final int INTRUDER_PHOTO_MIN_STAGE = 5;
    /** Rayon de veille nocturne pour le rapport du journal. */
    private static final double NIGHT_WATCH_QUERY_RADIUS = NightWatchLog.WATCH_RADIUS;
    /** Chance du one-shot villager_gaze par joueur et par échéance. */
    private static final int VILLAGER_GAZE_ONE_IN = 500;
    /** Rayon du regard simultané des villageois. */
    private static final double VILLAGER_GAZE_RADIUS = 24.0;
    /** Chance et stade du one-shot fake_leave (multijoueur). */
    private static final int FAKE_LEAVE_ONE_IN = 600;
    private static final int FAKE_LEAVE_MIN_STAGE = 4;
    /** Chance et stade du one-shot copy_occupant. */
    private static final int COPY_OCCUPANT_ONE_IN = 250;
    private static final int COPY_OCCUPANT_MIN_STAGE = 5;

    /** Dernier retri de coffre, par joueur (transitoire). */
    private static final Map<UUID, Long> LAST_CHEST_SHUFFLE = new HashMap<>();
    /** Dernière lecture du journal, par joueur (transitoire). */
    private static final Map<UUID, Long> LAST_JOURNAL_READ = new HashMap<>();

    private PersonalMoments() {
    }

    /** Boucle serveur (enregistrée dans {@code PenumbraMod.onInitialize}). */
    public static void onServerTick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_INTERVAL_TICKS != 0
                || !PenumbraConfig.get().personalEventsEnabled) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || !player.isAlive()
                    || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            sampleNightWatch(level, player);
            if (PenumbraConfig.get().storyEnabled && StoryManager.getStage(player) < 3) {
                continue; // la peur personnelle attend que l'Hôte connaisse le refuge
            }
            RandomSource random = player.getRandom();
            if (random.nextInt(ANIMAL_GAZE_ONE_IN) == 0) {
                tryAnimalGaze(level, player);
            }
            if (PenumbraConfig.get().metaMessagesEnabled
                    && server.getPlayerList().getPlayerCount() == 1
                    && random.nextInt(FAKE_JOIN_ONE_IN) == 0) {
                tryFakeJoin(level, player);
            }
            if (StoryManager.getStage(player) >= INTRUDER_PHOTO_MIN_STAGE
                    && random.nextInt(INTRUDER_PHOTO_ONE_IN) == 0) {
                tryIntruderPhoto(level, player);
            }
            if (random.nextInt(VILLAGER_GAZE_ONE_IN) == 0) {
                tryVillagerGaze(level, player);
            }
            if (PenumbraConfig.get().metaMessagesEnabled
                    && PenumbraConfig.get().multiplayerContent
                    && server.getPlayerList().getPlayerCount() >= 2
                    && StoryManager.getStage(player) >= FAKE_LEAVE_MIN_STAGE
                    && random.nextInt(FAKE_LEAVE_ONE_IN) == 0) {
                tryFakeLeave(level, player);
            }
            if (StoryManager.getStage(player) >= COPY_OCCUPANT_MIN_STAGE
                    && random.nextInt(COPY_OCCUPANT_ONE_IN) == 0) {
                tryCopyOccupant(level, player);
            }
        }
    }

    /**
     * One-shot {@code villager_gaze} (0.16.0) : tous les villageois à ≤ 24
     * blocs (3 minimum) se figent et fixent SIMULTANÉMENT le même point —
     * un point situé derrière le joueur. Aucun son. Trois secondes. Puis ils
     * reprennent leur vie comme si de rien n'était.
     */
    private static void tryVillagerGaze(ServerLevel level, ServerPlayer player) {
        PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
        String momentKey = oneShotKey("villager_gaze", player);
        if (state.isOneShotUsed(momentKey)) {
            return;
        }
        List<Villager> villagers = new ArrayList<>(level.getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(VILLAGER_GAZE_RADIUS)));
        villagers.removeIf(villager -> villager.isBaby() || villager.isNoAi()
                || !villager.isAlive() || villager.isSleeping());
        if (villagers.size() < 3) {
            return;
        }
        state.markOneShot(momentKey);
        // Le point : dans le dos du joueur, à hauteur d'yeux. Il n'y a rien.
        Vec3 look = player.getLookAngle();
        Vec3 behind = player.getEyePosition()
                .subtract(new Vec3(look.x, 0.0, look.z).normalize().scale(6.0));
        int count = Math.min(ANIMAL_GAZE_MAX, villagers.size());
        for (int i = 0; i < count; i++) {
            Villager villager = villagers.get(i);
            villager.getNavigation().stop();
            villager.setDeltaMovement(Vec3.ZERO);
            villager.lookAt(EntityAnchorArgument.Anchor.EYES, behind);
            villager.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    ANIMAL_GAZE_TICKS, 10, false, false, false));
        }
        debugLog("villager_gaze : {} villageois fixent le dos de {}",
                count, player.getGameProfile().name());
    }

    /**
     * One-shot {@code fake_leave} (0.16.0, multijoueur) : « X a quitté la
     * partie » — alors que X est toujours là, peut-être juste à côté. Seule
     * la victime reçoit le message ; X revient « en ligne » 30 à 60 s plus
     * tard. Clés vanilla, rendues dans la langue du client.
     */
    private static void tryFakeLeave(ServerLevel level, ServerPlayer victim) {
        PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
        String momentKey = oneShotKey("fake_leave", victim);
        if (state.isOneShotUsed(momentKey)) {
            return;
        }
        List<ServerPlayer> others = new ArrayList<>(
                level.getServer().getPlayerList().getPlayers());
        others.removeIf(other -> other == victim || other.isSpectator());
        if (others.isEmpty()) {
            return;
        }
        state.markOneShot(momentKey);
        ServerPlayer subject = others.get(victim.getRandom().nextInt(others.size()));
        String subjectName = subject.getGameProfile().name();
        UUID victimId = victim.getUUID();
        victim.sendSystemMessage(Component.translatable("multiplayer.player.left",
                Component.literal(subjectName)).withStyle(ChatFormatting.YELLOW));
        int returnDelay = 600 + victim.getRandom().nextInt(600);
        HorrorDirector.runLater(level, returnDelay, () -> {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(victimId);
            if (target != null) {
                target.sendSystemMessage(Component.translatable("multiplayer.player.joined",
                        Component.literal(subjectName)).withStyle(ChatFormatting.YELLOW));
            }
        });
        debugLog("fake_leave : '{}' semble parti pour {}", subjectName,
                victim.getGameProfile().name());
    }

    /**
     * One-shot {@code copy_occupant} (0.16.0, stade ≥ 5) : si une copie
     * imparfaite du refuge est encore debout à 20–40 blocs, quelque chose s'y
     * tient désormais — un Hôte DORMANT, dos au joueur, immobile comme une
     * statue (0.14.0 : il ne respire pas tant qu'il n'est pas en chasse).
     * Le regarder assez longtemps le fera disparaître, comme toujours.
     */
    private static void tryCopyOccupant(ServerLevel level, ServerPlayer player) {
        PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
        String momentKey = oneShotKey("copy_occupant", player);
        if (state.isOneShotUsed(momentKey) || !PenumbraConfig.get().hostEnabled) {
            return;
        }
        BlockPos closest = null;
        double closestSq = Double.MAX_VALUE;
        for (BlockPos site : CopyTracker.get(level)
                .sitesFor(player.getUUID(), level.dimension())) {
            double distSq = player.distanceToSqr(Vec3.atCenterOf(site));
            if (distSq < closestSq) {
                closestSq = distSq;
                closest = site;
            }
        }
        if (closest == null || closestSq < 20.0 * 20.0 || closestSq > 40.0 * 40.0) {
            return; // trop près (apparition injuste) ou trop loin (invisible)
        }
        HostEntity occupant = new HostEntity(PenumbraEntities.HOST, level);
        occupant.setOwner(player);
        // Dos au joueur : le yaw regarde à l'opposé de lui.
        Vec3 away = Vec3.atCenterOf(closest).subtract(player.position());
        float yaw = (float) (Math.atan2(away.z, away.x) * (180.0 / Math.PI)) - 90.0f;
        occupant.snapTo(closest.getX() + 0.5, closest.getY(), closest.getZ() + 0.5, yaw, 0.0f);
        occupant.setBehavior(HostBehavior.DORMANT);
        if (level.addFreshEntity(occupant)) {
            state.markOneShot(momentKey);
            debugLog("copy_occupant : l'Hôte occupe la copie de {}",
                    player.getGameProfile().name());
        }
    }

    /** Alimente le journal de veille : distance du plus proche Hôte, la nuit. */
    private static void sampleNightWatch(ServerLevel level, ServerPlayer player) {
        double nearestSq = Double.NaN;
        for (HostEntity host : level.getEntitiesOfClass(HostEntity.class,
                player.getBoundingBox().inflate(NIGHT_WATCH_QUERY_RADIUS))) {
            double distSq = host.distanceToSqr(player);
            if (Double.isNaN(nearestSq) || distSq < nearestSq) {
                nearestSq = distSq;
            }
        }
        NightWatchLog.sample(player.getUUID(), level.isDarkOutside(), nearestSq);
    }

    /** Purge de l'état transitoire d'un joueur (déconnexion). */
    public static void onPlayerDisconnect(UUID playerId) {
        // Le cooldown du coffre survit à une reconnexion pendant la session serveur.
        LAST_JOURNAL_READ.remove(playerId);
        NightWatchLog.forget(playerId);
    }

    public static void onServerStopped(MinecraftServer server) {
        LAST_CHEST_SHUFFLE.clear();
        LAST_JOURNAL_READ.clear();
        NightWatchLog.clearAll();
    }

    // ================= Réveil : coffre retrié + bed_shift =================

    /** Appelé par {@code HorrorDirector.noteSlept} à chaque réveil au matin. */
    public static void onWake(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.personalEventsEnabled
                || !(player.level() instanceof ServerLevel level)
                || (config.storyEnabled && StoryManager.getStage(player) < 3)) {
            return;
        }
        RandomSource random = player.getRandom();
        long gameTime = level.getGameTime();
        if (config.worldModificationEnabled) {
            Long last = LAST_CHEST_SHUFFLE.get(player.getUUID());
            boolean shuffleReady = last == null
                    || gameTime - last >= CHEST_SHUFFLE_COOLDOWN_TICKS;
            if (shuffleReady && random.nextFloat() < CHEST_SHUFFLE_CHANCE
                    && hauntNearbyChest(level, player)) {
                LAST_CHEST_SHUFFLE.put(player.getUUID(), gameTime);
                return; // jamais les deux le même matin
            }
            PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
            String bedMoment = oneShotKey("bed_shift", player);
            if (!state.isOneShotUsed(bedMoment) && random.nextFloat() < BED_SHIFT_CHANCE
                    && shiftBed(level, player)) {
                state.markOneShot(bedMoment);
                return;
            }
        }
        trySleepPhoto(level, player, random, gameTime);
    }

    /**
     * One-shot {@code sleep_photo} (0.16.0, stade ≥ 4) : au réveil, une
     * photographie apparaît dans l'inventaire — votre lit, cadré depuis
     * l'embrasure de la porte, avec une silhouette d'un pixel. Le cliché est
     * réellement ajouté à l'album (pixels générés par {@link FauxPhotoPainter}).
     * Aucun message : le joueur la découvre en rangeant ses affaires.
     */
    private static void trySleepPhoto(ServerLevel level, ServerPlayer player,
                                      RandomSource random, long gameTime) {
        PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
        String momentKey = oneShotKey("sleep_photo", player);
        if (state.isOneShotUsed(momentKey)
                || StoryManager.getStage(player) < SLEEP_PHOTO_MIN_STAGE
                || random.nextFloat() >= SLEEP_PHOTO_CHANCE) {
            return;
        }
        byte[] pixels = FauxPhotoPainter.bedroomAtNight(random.nextLong());
        PhotoAlbumState.get(level.getServer())
                .add(player.getUUID(), gameTime, pixels)
                .ifPresent(stored -> {
                    state.markOneShot(momentKey);
                    ItemStack photo = PhotoContent.createPhoto(new PhotoData(
                            stored.id(), stored.capturedAtTick(),
                            stored.projectionist()), gameTime);
                    if (!player.getInventory().add(photo)) {
                        player.drop(photo, false);
                    }
                    debugLog("sleep_photo : cliché nocturne remis à {}",
                            player.getGameProfile().name());
                });
    }

    /**
     * One-shot {@code intruder_photo} (0.16.0, stade ≥ 5) : une photographie
     * que le joueur n'a jamais prise se glisse dans son album — sa bâtisse vue
     * de dehors, de nuit, une fenêtre allumée. Aucun objet, aucun message :
     * elle attend d'être découverte à la prochaine ouverture de l'album.
     */
    private static void tryIntruderPhoto(ServerLevel level, ServerPlayer player) {
        PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
        String momentKey = oneShotKey("intruder_photo", player);
        if (state.isOneShotUsed(momentKey)) {
            return;
        }
        byte[] pixels = FauxPhotoPainter.baseFromOutside(player.getRandom().nextLong());
        PhotoAlbumState.get(level.getServer())
                .add(player.getUUID(), level.getGameTime(), pixels)
                .ifPresent(stored -> {
                    state.markOneShot(momentKey);
                    debugLog("intruder_photo : cliché intrus glissé dans l'album de {}",
                            player.getGameProfile().name());
                });
    }

    /**
     * Simule le déplacement d'un coffre sans jamais ouvrir son conteneur : le
     * contenu, l'ordre des slots et les automatisations restent strictement intacts.
     */
    private static boolean hauntNearbyChest(ServerLevel level, ServerPlayer player) {
        BlockPos chest = findNearbyBlock(level, player.blockPosition(), 8,
                state -> state.getBlock() instanceof ChestBlock);
        if (chest == null) {
            return false;
        }
        Vec3 sound = Vec3.atCenterOf(chest).add(0.0, -0.15, 0.0);
        PenumbraNetworking.sendSoundPrioritySpatial(
                player, PenumbraSounds.HOST_STEP, sound, 0.7f, 0.62f);
        debugLog("Écho de coffre près de {}", player.getGameProfile().name());
        return true;
    }

    /**
     * One-shot historique {@code bed_shift}. Il ne déplace plus physiquement le
     * lit : cela invalidait le point de réapparition. Le son ciblé sous le lit
     * conserve l'impression que quelque chose vient d'y bouger.
     */
    private static boolean shiftBed(ServerLevel level, ServerPlayer player) {
        BlockPos foot = findNearbyBlock(level, player.blockPosition(), 4,
                state -> state.getBlock() instanceof BedBlock
                        && state.getValue(BedBlock.PART) == BedPart.FOOT);
        if (foot == null) {
            return false;
        }
        PenumbraNetworking.sendSoundPriority(player, PenumbraSounds.HOST_STEP,
                Vec3.atCenterOf(foot).add(0.0, -0.5, 0.0), 0.45f, 0.72f);
        debugLog("bed_shift sécurisé : mouvement entendu sous le lit de {}",
                player.getGameProfile().name());
        return true;
    }

    // ================= One-shots à la volée =================

    /**
     * One-shot {@code animal_gaze} : tous les animaux à ≤ 20 blocs (12 max)
     * se figent et fixent le joueur SIMULTANÉMENT pendant 3 s, puis reprennent
     * (technique {@code lookAt} + ralentissement vanilla temporisé, qui survit
     * proprement à une sauvegarde). Une seule fois par joueur et par monde.
     */
    private static void tryAnimalGaze(ServerLevel level, ServerPlayer player) {
        PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
        String momentKey = oneShotKey("animal_gaze", player);
        if (state.isOneShotUsed(momentKey)) {
            return;
        }
        List<Animal> animals = new ArrayList<>(level.getEntitiesOfClass(Animal.class,
                player.getBoundingBox().inflate(ANIMAL_GAZE_RADIUS)));
        animals.removeIf(animal -> animal.isBaby() || animal.isNoAi() || !animal.isAlive());
        if (animals.size() < 3) {
            return; // moins de trois témoins : l'effet de meute n'existe pas
        }
        state.markOneShot(momentKey);
        int count = Math.min(ANIMAL_GAZE_MAX, animals.size());
        for (int i = 0; i < count; i++) {
            Animal animal = animals.get(i);
            animal.getNavigation().stop();
            animal.setDeltaMovement(Vec3.ZERO);
            animal.lookAt(EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
            // Un effet temporisé vanilla expire aussi après sauvegarde/rechargement,
            // contrairement à setNoAi + tâche mémoire qui pouvait rester bloqué.
            animal.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    ANIMAL_GAZE_TICKS, 10, false, false, false));
        }
        debugLog("animal_gaze : {} animaux fixent {}", count, player.getGameProfile().name());
    }

    /**
     * One-shot {@code fake_join} (solo, {@code metaMessagesEnabled}) : faux
     * message vanilla « X a rejoint la partie » avec le pseudo du joueur à une
     * lettre près, puis « X a quitté la partie » 60–120 s plus tard. Clés
     * vanilla {@code multiplayer.player.joined/left} : rendues dans la langue
     * du client — français si le jeu est en français.
     */
    private static void tryFakeJoin(ServerLevel level, ServerPlayer player) {
        PenumbraPersistentState state = PenumbraPersistentState.get(level.getServer());
        String momentKey = oneShotKey("fake_join", player);
        if (state.isOneShotUsed(momentKey)) {
            return;
        }
        state.markOneShot(momentKey);
        String altName = swapOneLetter(player.getGameProfile().name(), player.getRandom());
        UUID playerId = player.getUUID();
        player.sendSystemMessage(Component.translatable("multiplayer.player.joined",
                Component.literal(altName)).withStyle(ChatFormatting.YELLOW));
        int leaveDelay = 1200 + player.getRandom().nextInt(1200);
        HorrorDirector.runLater(level, leaveDelay, () -> {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
            if (target != null) {
                target.sendSystemMessage(Component.translatable("multiplayer.player.left",
                        Component.literal(altName)).withStyle(ChatFormatting.YELLOW));
            }
        });
        debugLog("fake_join : '{}' pour {}", altName, player.getGameProfile().name());
    }

    /** Échange deux lettres adjacentes du pseudo (ou double la dernière si trop court). */
    private static String swapOneLetter(String name, RandomSource random) {
        if (name.length() < 3) {
            return name + name.charAt(name.length() - 1);
        }
        int at = 1 + random.nextInt(name.length() - 2);
        return name.substring(0, at) + name.charAt(at + 1) + name.charAt(at)
                + name.substring(at + 2);
    }

    // ================= Journal incomplet =================

    /**
     * Lecture du Journal incomplet (clic droit, appelé par {@code JournalItem}) :
     * quatre lignes générées depuis les vraies statistiques du profil, en gris
     * italique (convention lore). Clés {@code penumbra.journal.*} — françaises
     * dans fr_fr.json. Anti-spam 5 s.
     */
    public static void onJournalUse(ServerPlayer player) {
        long gameTime = player.level().getGameTime();
        Long last = LAST_JOURNAL_READ.get(player.getUUID());
        if (last != null && gameTime - last < JOURNAL_COOLDOWN_TICKS) {
            return;
        }
        LAST_JOURNAL_READ.put(player.getUUID(), gameTime);
        PlayerHorrorProfile profile = PenumbraPersistentState
                .get(player.level().getServer()).profile(player.getUUID());
        player.sendSystemMessage(style("penumbra.journal.header"));
        player.sendSystemMessage(style("penumbra.journal.beds", profile.bedUses()));
        player.sendSystemMessage(style("penumbra.journal.looks", profile.lookBackCount()));
        player.sendSystemMessage(style("penumbra.journal.underground",
                profile.ticksUnderground() / 1200L));
        player.sendSystemMessage(style("penumbra.journal.events", profile.totalEvents()));
        // 0.16.0 — le rapport de la dernière nuit : la peur rétroactive.
        NightWatchLog.NightSummary night = NightWatchLog.summaryFor(player.getUUID());
        if (night != null) {
            if (night.approaches() > 0) {
                player.sendSystemMessage(style("penumbra.journal.night.approaches",
                        night.approaches()));
                player.sendSystemMessage(style("penumbra.journal.night.closest",
                        night.closestBlocks()));
            } else {
                player.sendSystemMessage(style("penumbra.journal.night.quiet"));
            }
        }
        if (profile.totalEvents() > 0) {
            player.sendSystemMessage(style("penumbra.journal.footer"));
        }
    }

    private static Component style(String key, Object... args) {
        return Component.translatable(key, args)
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC);
    }

    private static String oneShotKey(String id, ServerPlayer player) {
        return id + ":" + player.getUUID();
    }

    // ================= Helpers =================

    /** Premier bloc satisfaisant le prédicat dans un cube borné, ou null. */
    private static BlockPos findNearbyBlock(ServerLevel level, BlockPos center, int radius,
                                            java.util.function.Predicate<BlockState> predicate) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (level.isLoaded(cursor) && predicate.test(level.getBlockState(cursor))) {
                        return cursor.immutable();
                    }
                }
            }
        }
        return null;
    }

    private static void debugLog(String message, Object... args) {
        if (PenumbraConfig.get().debugLogging) {
            PenumbraMod.LOGGER.info("[Penumbra] " + message, args);
        }
    }
}
