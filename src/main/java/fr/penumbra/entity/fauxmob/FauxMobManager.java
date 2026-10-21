package fr.penumbra.entity.fauxmob;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.entity.fauxmob.FauxMobRules.Defect;
import fr.penumbra.entity.fauxmob.FauxMobRules.Disguise;
import fr.penumbra.entity.fauxmob.FauxMobRules.RevealCause;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pilote les Faux-Semblants : de vraies créatures vanilla possédées (0.15.0).
 *
 * <p>Aucune entité custom : le serveur fait apparaître une vache, un cochon, un
 * mouton, une poule — ou un zombie la nuit — et lui impose un défaut discret
 * ({@link Defect}). La créature reste physiquement authentique pour tous les
 * joueurs ; c'est le doute qui est l'arme. Règles dures héritées du reste du
 * mod :</p>
 *
 * <ul>
 *   <li><b>jamais de NoAI</b> — l'immobilité passe par navigation coupée et
 *       lenteur vanilla temporisée : un arrêt serveur ne laisse jamais un
 *       animal définitivement figé (doctrine {@code AnimalStareEvent}) ;</li>
 *   <li><b>jamais touchable</b> — approche, coup, interaction ou flash de la
 *       Caméra VHS dissolvent la créature en cendres avec un chuchotement ;</li>
 *   <li><b>jamais de disparition observée gratuite</b> — l'expiration
 *       silencieuse n'a lieu que hors du regard du joueur ;</li>
 *   <li><b>copies parfaites</b> — {@link FauxMobRules#pickDefect} produit des
 *       créatures sans défaut, que seul le contact ou le flash trahit. Leur
 *       part augmente avec le vécu du joueur.</li>
 * </ul>
 *
 * <p>État de session uniquement (aucune persistance) ; thread serveur.</p>
 */
public final class FauxMobManager {

    /** Cadence de pilotage (ticks). */
    private static final int TICK_INTERVAL = 2;
    /** Cadence de rafraîchissement de la lenteur vanilla. */
    private static final int SLOWNESS_REFRESH_TICKS = 40;
    /** Durée d'une dose de lenteur : couvre deux rafraîchissements. */
    private static final int SLOWNESS_DURATION_TICKS = 90;
    /** Distance minimale et maximale d'apparition. */
    private static final double SPAWN_MIN_DIST = 18.0;
    private static final double SPAWN_MAX_DIST = 34.0;
    /** Écart vertical maximal accepté avec le joueur (surface). */
    private static final int SPAWN_MAX_DY = 8;
    /** Portée et cône du flash de la Caméra VHS. */
    private static final double FLASH_RANGE = 24.0;
    private static final double FLASH_CONE_DOT = 0.35;
    /** Sessions maximales simultanées par serveur. */
    private static final int MAX_SESSIONS = 8;

    private static final Map<MinecraftServer, ServerState> STATES = new IdentityHashMap<>();

    private FauxMobManager() {
    }

    private static final class ServerState {
        final List<Session> sessions = new ArrayList<>();
        /** Rencontres vécues par joueur (session serveur) : nourrit la part de parfaits. */
        final Map<UUID, Integer> encounters = new HashMap<>();
    }

    private static final class Session {
        final UUID mobId;
        final UUID ownerId;
        final ServerLevel level;
        final Disguise disguise;
        final Defect defect;
        final long spawnGameTime;
        final Vec3 frozenGaze;
        int stareTicks;
        long revealAtGameTime = Long.MIN_VALUE;
        long lastSlownessGameTime = Long.MIN_VALUE;

        Session(UUID mobId, UUID ownerId, ServerLevel level, Disguise disguise,
                Defect defect, long spawnGameTime, Vec3 frozenGaze) {
            this.mobId = mobId;
            this.ownerId = ownerId;
            this.level = level;
            this.disguise = disguise;
            this.defect = defect;
            this.spawnGameTime = spawnGameTime;
            this.frozenGaze = frozenGaze;
        }
    }

    /** Enregistre les callbacks d'interaction (appelé une fois au démarrage). */
    public static void init() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()) {
                onTargeted(entity, RevealCause.STRUCK);
            }
            return InteractionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()) {
                onTargeted(entity, RevealCause.TOUCHED);
            }
            return InteractionResult.PASS;
        });
    }

    /**
     * Tirage naturel complet pour ce joueur (déguisement, défaut, placement).
     * Retourne {@code false} sans aucun effet si aucun emplacement honnête
     * n'existe — l'événement appelant ne compte alors pas.
     */
    public static boolean spawnNatural(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        PenumbraConfig config = PenumbraConfig.get();
        RandomSource random = player.getRandom();
        boolean zombieAllowed = config.fauxCreatureZombieEnabled
                && level.dimension() == Level.OVERWORLD && level.isDarkOutside();
        Disguise disguise = FauxMobRules.pickDisguise(random.nextFloat(), zombieAllowed);
        int seen = encountersOf(level.getServer(), player.getUUID());
        Defect defect = FauxMobRules.pickDefect(random.nextFloat(), random.nextFloat(), seen);
        return spawnFor(player, disguise, defect);
    }

    /** Déguisement aléatoire, défaut imposé (commandes de test). */
    public static boolean spawnWithDefect(ServerPlayer player, Defect defect) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        PenumbraConfig config = PenumbraConfig.get();
        boolean zombieAllowed = config.fauxCreatureZombieEnabled
                && level.dimension() == Level.OVERWORLD && level.isDarkOutside();
        Disguise disguise = FauxMobRules.pickDisguise(
                player.getRandom().nextFloat(), zombieAllowed);
        return spawnFor(player, disguise, defect);
    }

    /** Apparition contrôlée (événement naturel ou commande de test). */
    public static boolean spawnFor(ServerPlayer player, Disguise disguise, Defect defect) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        ServerState state = state(level.getServer());
        if (state.sessions.size() >= MAX_SESSIONS) {
            return false;
        }
        RandomSource random = level.getRandom();
        BlockPos spot = findSpot(level, player, random);
        if (spot == null) {
            return false;
        }

        Mob mob = createDisguise(disguise, level);
        if (mob == null) {
            return false;
        }
        float yaw = random.nextFloat() * 360.0f;
        mob.snapTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0.0f);
        if (defect == Defect.SILENT) {
            mob.setSilent(true);
        }
        if (disguise == Disguise.ZOMBIE) {
            // Sans persistance, le zombie pourrait despawner en pleine scène.
            // Les animaux passifs, eux, ne despawnent jamais naturellement.
            mob.setPersistenceRequired();
        }
        if (!level.addFreshEntity(mob)) {
            return false;
        }

        // Regard figé du défaut FROZEN : un point fixe sans intérêt, à hauteur d'yeux.
        double gazeAngle = random.nextDouble() * (Math.PI * 2.0);
        Vec3 gaze = mob.getEyePosition().add(
                Math.cos(gazeAngle) * 24.0, 0.0, Math.sin(gazeAngle) * 24.0);

        state.sessions.add(new Session(mob.getUUID(), player.getUUID(), level,
                disguise, defect, level.getGameTime(), gaze));
        state.encounters.merge(player.getUUID(), 1, Integer::sum);
        return true;
    }

    /** Tick serveur : pilotage des défauts, regard, contact, expiration. */
    public static void onServerTick(MinecraftServer server) {
        ServerState state = STATES.get(server);
        if (state == null || state.sessions.isEmpty()) {
            return;
        }
        if (server.getTickCount() % TICK_INTERVAL != 0) {
            return;
        }
        Iterator<Session> iterator = state.sessions.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            Entity entity = session.level.getEntity(session.mobId);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                iterator.remove();
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(session.ownerId);
            if (owner == null || owner.level() != session.level) {
                // Propriétaire parti : la créature s'efface sans témoin ni effet.
                mob.discard();
                iterator.remove();
                continue;
            }
            if (tickSession(session, mob, owner)) {
                iterator.remove();
            }
        }
    }

    /** @return true si la session est terminée. */
    private static boolean tickSession(Session session, Mob mob, ServerPlayer owner) {
        long gameTime = session.level.getGameTime();
        long age = gameTime - session.spawnGameTime;

        applyDefect(session, mob, owner, gameTime);

        double distanceSq = owner.distanceToSqr(mob);
        if (distanceSq < FauxMobRules.TOUCH_DISTANCE * FauxMobRules.TOUCH_DISTANCE) {
            dissolve(session, mob, owner, RevealCause.TOUCHED);
            return true;
        }

        Vec3 toMob = mob.getEyePosition().subtract(owner.getEyePosition());
        double dot = toMob.lengthSqr() < 1.0E-4 ? 1.0
                : owner.getViewVector(1.0f).dot(toMob.normalize());

        if (session.revealAtGameTime != Long.MIN_VALUE) {
            // Elle se sait vue : elle rend le regard, puis s'efface.
            holdStill(mob);
            mob.lookAt(EntityAnchorArgument.Anchor.EYES, owner.getEyePosition());
            if (gameTime >= session.revealAtGameTime) {
                dissolve(session, mob, owner, RevealCause.STARED);
                return true;
            }
            return false;
        }

        if (FauxMobRules.stareCanReveal(session.defect)
                && FauxMobRules.isStaring(dot, distanceSq)
                && owner.hasLineOfSight(mob)) {
            session.stareTicks += TICK_INTERVAL;
            if (session.stareTicks >= FauxMobRules.STARE_REVEAL_TICKS) {
                session.revealAtGameTime = gameTime + FauxMobRules.HEAD_TURN_TICKS;
            }
        }

        if (FauxMobRules.shouldTimeout(age, FauxMobRules.isUnseen(dot, distanceSq))) {
            // Hors du regard : la créature n'est simplement plus là.
            dissolve(session, mob, owner, RevealCause.TIMEOUT);
            return true;
        }
        return false;
    }

    /** Applique le défaut de la créature, sans jamais toucher au drapeau NoAI. */
    private static void applyDefect(Session session, Mob mob, ServerPlayer owner, long gameTime) {
        boolean restrained = session.defect == Defect.STARE
                || session.defect == Defect.FROZEN
                || session.disguise == Disguise.ZOMBIE;
        if (!restrained) {
            return; // SILENT et parfaits vivent leur vie normale d'animal.
        }
        holdStill(mob);
        if (gameTime - session.lastSlownessGameTime >= SLOWNESS_REFRESH_TICKS) {
            session.lastSlownessGameTime = gameTime;
            mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,
                    SLOWNESS_DURATION_TICKS, 10, false, false, false));
        }
        if (session.defect == Defect.STARE) {
            mob.lookAt(EntityAnchorArgument.Anchor.EYES, owner.getEyePosition());
        } else {
            mob.lookAt(EntityAnchorArgument.Anchor.EYES, session.frozenGaze);
        }
    }

    private static void holdStill(Mob mob) {
        mob.getNavigation().stop();
        mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.0, 1.0, 0.0));
    }

    /** Coup ou interaction d'un joueur sur une créature possédée. */
    private static void onTargeted(Entity entity, RevealCause cause) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }
        ServerState state = STATES.get(level.getServer());
        if (state == null) {
            return;
        }
        Iterator<Session> iterator = state.sessions.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (!session.mobId.equals(entity.getUUID())) {
                continue;
            }
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(session.ownerId);
            if (entity instanceof Mob mob) {
                dissolve(session, mob, owner, cause);
            }
            iterator.remove();
            return;
        }
    }

    /** Flash de la Caméra VHS : révèle les Faux-Semblants dans le cône. */
    public static void onCameraFlash(ServerPlayer photographer) {
        if (!(photographer.level() instanceof ServerLevel level)) {
            return;
        }
        ServerState state = STATES.get(level.getServer());
        if (state == null || state.sessions.isEmpty()) {
            return;
        }
        Vec3 view = photographer.getViewVector(1.0f);
        Iterator<Session> iterator = state.sessions.iterator();
        while (iterator.hasNext()) {
            Session session = iterator.next();
            if (session.level != level) {
                continue;
            }
            Entity entity = level.getEntity(session.mobId);
            if (!(entity instanceof Mob mob) || !mob.isAlive()) {
                iterator.remove();
                continue;
            }
            Vec3 toMob = mob.getEyePosition().subtract(photographer.getEyePosition());
            if (toMob.lengthSqr() > FLASH_RANGE * FLASH_RANGE
                    || view.dot(toMob.normalize()) < FLASH_CONE_DOT
                    || !photographer.hasLineOfSight(mob)) {
                continue;
            }
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(session.ownerId);
            dissolve(session, mob, owner, RevealCause.FLASHED);
            iterator.remove();
        }
    }

    /** Dissolution : cendres, chuchotement, tension — ou rien du tout (timeout). */
    private static void dissolve(Session session, Mob mob, ServerPlayer owner, RevealCause cause) {
        if (!FauxMobRules.isSilentDissolve(cause)) {
            Vec3 pos = mob.position();
            session.level.sendParticles(PenumbraParticles.ASH_MOTE,
                    pos.x, pos.y + mob.getBbHeight() * 0.6, pos.z,
                    12, 0.25, 0.3, 0.25, 0.01);
            if (owner != null) {
                // 0.18.0 : la créature vanilla possédée n'a pas de type propre ;
                // le balayage visuel du Relevé ne peut donc pas la voir. Sa
                // dissolution est le seul instant où le joueur sait ce que
                // c'était : c'est là que la page se remplit.
                fr.penumbra.journal.JournalManager.noteSeen(
                        owner, fr.penumbra.journal.JournalEntry.FAUX_CREATURE);
                PenumbraNetworking.sendSound(owner, PenumbraSounds.HOST_WHISPER,
                        pos, 0.4f, 0.72f + session.level.getRandom().nextFloat() * 0.1f);
                if (cause == RevealCause.STRUCK) {
                    PenumbraNetworking.sendJumpscare(owner, "sting", 0.5f);
                }
                float tension = HorrorDirector.getTension(
                        session.level.getServer(), owner.getUUID());
                HorrorDirector.setTension(owner,
                        tension + FauxMobRules.tensionGain(cause));
            }
        }
        mob.discard();
    }

    /** Placement honnête : au sol, dans le dos, jamais dans le champ de vision. */
    private static BlockPos findSpot(ServerLevel level, ServerPlayer target, RandomSource random) {
        Vec3 look = target.getLookAngle().normalize();
        double lookAngle = Math.atan2(look.z, look.x);
        for (int attempt = 0; attempt < 20; attempt++) {
            double angle = lookAngle + Math.PI + (random.nextDouble() - 0.5) * (Math.PI * 1.4);
            double dist = SPAWN_MIN_DIST
                    + random.nextDouble() * (SPAWN_MAX_DIST - SPAWN_MIN_DIST);
            double dx = Math.cos(angle) * dist;
            double dz = Math.sin(angle) * dist;

            Vec3 flatLook = new Vec3(look.x, 0.0, look.z);
            if (flatLook.lengthSqr() > 1.0E-4) {
                double inv = 1.0 / dist;
                if (flatLook.normalize().dot(new Vec3(dx * inv, 0.0, dz * inv)) > 0.2) {
                    continue;
                }
            }
            int x = Mth.floor(target.getX() + dx);
            int z = Mth.floor(target.getZ() + dz);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (Math.abs(y - Mth.floor(target.getY())) > SPAWN_MAX_DY) {
                continue;
            }
            return new BlockPos(x, y, z);
        }
        return null;
    }

    private static Mob createDisguise(Disguise disguise, ServerLevel level) {
        return switch (disguise) {
            case COW -> new Cow(EntityType.COW, level);
            case PIG -> new Pig(EntityType.PIG, level);
            case SHEEP -> new Sheep(EntityType.SHEEP, level);
            case CHICKEN -> new Chicken(EntityType.CHICKEN, level);
            case ZOMBIE -> new Zombie(EntityType.ZOMBIE, level);
        };
    }

    private static int encountersOf(MinecraftServer server, UUID playerId) {
        ServerState state = STATES.get(server);
        return state == null ? 0 : state.encounters.getOrDefault(playerId, 0);
    }

    private static ServerState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, s -> new ServerState());
    }

    /** Sessions actives (commandes de debug). */
    public static int activeSessions(MinecraftServer server) {
        ServerState state = STATES.get(server);
        return state == null ? 0 : state.sessions.size();
    }

    /**
     * Créatures possédées vivantes de ce niveau — pour la preuve photo : le
     * détecteur s'exécute avant la dissolution du flash, le cliché garde donc
     * la trace de ce qui n'était pas un animal.
     */
    public static List<Mob> possessedMobsIn(ServerLevel level) {
        ServerState state = STATES.get(level.getServer());
        if (state == null || state.sessions.isEmpty()) {
            return List.of();
        }
        List<Mob> mobs = new ArrayList<>();
        for (Session session : state.sessions) {
            if (session.level != level) {
                continue;
            }
            if (level.getEntity(session.mobId) instanceof Mob mob && mob.isAlive()) {
                mobs.add(mob);
            }
        }
        return mobs;
    }

    /**
     * Arrêt propre : dissout toutes les scènes pendant que les mondes sont
     * encore vivants — aucune créature pilotée ne doit entrer en sauvegarde.
     */
    public static void onServerStopping(MinecraftServer server) {
        ServerState state = STATES.get(server);
        if (state == null) {
            return;
        }
        for (Session session : state.sessions) {
            if (session.level.getEntity(session.mobId) instanceof Mob mob) {
                mob.discard();
            }
        }
        state.sessions.clear();
    }

    /** Purge entre deux serveurs ; un crash laisse au pire un animal vanilla. */
    public static void onServerStopped(MinecraftServer server) {
        STATES.remove(server);
    }
}
