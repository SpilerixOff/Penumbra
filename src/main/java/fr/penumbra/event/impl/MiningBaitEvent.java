package fr.penumbra.event.impl;

import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.data.PenumbraPersistentState;
import fr.penumbra.director.HorrorDirector;
import fr.penumbra.director.PlayerHorrorProfile;
import fr.penumbra.director.TensionState;
import fr.penumbra.entity.HostBehavior;
import fr.penumbra.entity.HostEntity;
import fr.penumbra.event.HorrorEvent;
import fr.penumbra.event.underground.AbstractUndergroundEvent;
import fr.penumbra.network.PenumbraNetworking;
import fr.penumbra.registry.PenumbraEntities;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraParticles;
import fr.penumbra.registry.PenumbraSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * {@code mining_bait} (0.6.0) — LE screamer au retournement.
 * <p>
 * Scénario en quatre temps, sous terre, pioche ou marteau en main :
 * <ol>
 *   <li><b>Le leurre</b> : trois coups de pioche retentissent 6–9 blocs
 *       DERRIÈRE le joueur (± une voix qui aboie « HÉ » à haute tension) ;</li>
 *   <li><b>Le retournement</b> : le serveur guette la rotation du joueur
 *       (&gt; 80° par rapport au cap initial, fenêtre 15 s). Il se
 *       retourne… et il n'y a RIEN. Le rien est l'armement ;</li>
 *   <li><b>Le retour</b> : dès que son cap revient à &lt; 55° du cap initial
 *       (fenêtre 20 s), le piège se referme ;</li>
 *   <li><b>Le screamer</b> : l'Hôte se tient à ~3 blocs FACE à lui (s'il
 *       existe un emplacement valide), hurlement {@code SCREAMER_SHRIEK}
 *       prioritaire, visage plein écran côté client (type {@code "screamer"},
     *       respecte {@code screamersEnabled}), gain de tension centralisé par
     *       le directeur, dissolution en
 *       cendre 25 ticks plus tard.
 *   </li>
 * </ol>
 * S'il ne se retourne jamais (ou ne revient pas) : rien ne se passe, aucun
 * cooldown gâché côté joueur — le doute seul a travaillé. Suivi par polls
 * récursifs de 3 ticks (pattern {@code mimicPostureStep}), abandon propre sur
 * mort/déconnexion/timeout.
 * </p>
 */
public final class MiningBaitEvent implements HorrorEvent {

    /** Distance du leurre derrière le joueur (blocs). */
    private static final double BAIT_MIN_DIST = 6.0;
    private static final double BAIT_MAX_DIST = 9.0;
    /** Rotation minimale comptant comme retournement (degrés). */
    private static final float TURN_DEGREES = 80.0f;
    /** Retour : écart au cap initial sous lequel le piège se referme (degrés). */
    private static final float RETURN_DEGREES = 55.0f;
    /** Cadence du poll de rotation (ticks). */
    private static final int POLL_INTERVAL = 3;
    /** Fenêtre du retournement (polls : 100 × 3 = 300 ticks = 15 s). */
    private static final int TURN_MAX_POLLS = 100;
    /** Fenêtre du retour (polls : 134 × 3 = 402 ticks ≈ 20 s). */
    private static final int RETURN_MAX_POLLS = 134;
    /** Distance d'apparition de l'Hôte face au joueur (blocs). */
    private static final double FACE_DIST = 3.0;
    /** Rayon cohérent avec le garde-fou du directeur pour le quota d'Hôtes. */
    private static final double HOST_QUOTA_RADIUS = 64.0;
    /** Durée de vie de l'Hôte du screamer avant dissolution (ticks). */
    private static final int HOST_LINGER_TICKS = 25;
    @Override
    public String id() {
        return "mining_bait";
    }

    @Override
    public int weight(TensionState state) {
        PenumbraConfig config = PenumbraConfig.get();
        if (!config.undergroundEventsEnabled || !config.screamersEnabled
                || config.jumpscareFrequency <= 0.0f) {
            return 0;
        }
        int base = switch (state) {
            case CALM, UNEASE -> 0;
            case WATCHED -> 4;
            case MANIFESTATION -> 6;
            case HUNT -> 7;
            case CRISIS -> 5;
            case FALSE_CALM -> 6;
        };
        if (base == 0) {
            return 0;
        }
        return Math.max(1, Math.round(base * config.jumpscareFrequency));
    }

    @Override
    public int cooldownTicks() {
        return 6000; // 5 min : marquant, mais assez fréquent pour être rencontré
    }

    @Override
    public boolean canRun(ServerPlayer player, PlayerHorrorProfile profile) {
        return contextStillValid(player);
    }

    /**
     * Toutes les phases différées réutilisent exactement ce prédicat : quitter
     * la grotte, dormir, changer d'outil ou couper l'option désarme la scène.
     */
    private static boolean contextStillValid(ServerPlayer player) {
        PenumbraConfig config = PenumbraConfig.get();
        return config.undergroundEventsEnabled
                && config.screamersEnabled
                && config.jumpscareFrequency > 0.0f
                && player.isAlive()
                && !player.isSpectator()
                && !player.isSleeping()
                && AbstractUndergroundEvent.isUnderground(player)
                && holdsMiningTool(player);
    }

    /** Pioche vanilla/moddée via tag (les quatre marteaux y sont enregistrés). */
    private static boolean holdsMiningTool(ServerPlayer player) {
        return player.getMainHandItem().is(ItemTags.PICKAXES);
    }

    @Override
    public boolean run(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level) || !contextStillValid(player)) {
            return false;
        }
        RandomSource random = player.getRandom();
        float initialYaw = player.getYRot();

        // 1) Le leurre : trois coups de pioche derrière, cadence irrégulière.
        Vec3 look = player.getLookAngle().normalize();
        double dist = BAIT_MIN_DIST + random.nextDouble() * (BAIT_MAX_DIST - BAIT_MIN_DIST);
        Vec3 bait = player.position().subtract(look.scale(dist)).add(0.0, 1.0, 0.0);
        UUID playerId = player.getUUID();
        int delay = 0;
        for (int i = 0; i < 3; i++) {
            delay += 7 + random.nextInt(6);
            float pitch = 0.7f + random.nextFloat() * 0.15f;
            HorrorDirector.runLater(level, delay, () -> {
                ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
                if (target != null && target.level() == level && contextStillValid(target)) {
                    PenumbraNetworking.sendSound(target, SoundEvents.STONE_HIT, bait, 0.9f, pitch);
                }
            });
        }
        // À haute tension : la voix. Un seul mot, jamais répété dans la séquence.
        PlayerHorrorProfile profile = PenumbraPersistentState.get(level.getServer()).profile(playerId);
        if (profile.tension() >= TensionState.MANIFESTATION.threshold) {
            HorrorDirector.runLater(level, delay + 14, () -> {
                ServerPlayer target = level.getServer().getPlayerList().getPlayer(playerId);
                if (target != null && target.level() == level && contextStillValid(target)) {
                    PenumbraNetworking.sendSound(target, PenumbraSounds.VOICE_HEY, bait, 0.75f, 0.95f);
                }
            });
        }

        // 2) Armement de la détection de retournement après le dernier coup.
        HorrorDirector.runLater(level, delay + 20,
                () -> pollTurn(level, playerId, initialYaw, 0));
        return true;
    }

    /** Phase « retournement » : attend une rotation &gt; {@value #TURN_DEGREES}°. */
    private static void pollTurn(ServerLevel level, UUID playerId, float initialYaw, int poll) {
        HorrorDirector.runLater(level, POLL_INTERVAL, () -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || player.level() != level || !contextStillValid(player)
                    || poll >= TURN_MAX_POLLS) {
                return; // jamais retourné : le doute seul a travaillé
            }
            float delta = Math.abs(Mth.wrapDegrees(player.getYRot() - initialYaw));
            if (delta > TURN_DEGREES) {
                pollReturn(level, playerId, initialYaw, 0); // il regarde. Il n'y a rien.
            } else {
                pollTurn(level, playerId, initialYaw, poll + 1);
            }
        });
    }

    /** Phase « retour » : attend le retour au cap initial, puis referme le piège. */
    private static void pollReturn(ServerLevel level, UUID playerId, float initialYaw, int poll) {
        HorrorDirector.runLater(level, POLL_INTERVAL, () -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null || player.level() != level || !contextStillValid(player)
                    || poll >= RETURN_MAX_POLLS) {
                return; // il n'est jamais revenu face à son front de taille
            }
            float delta = Math.abs(Mth.wrapDegrees(player.getYRot() - initialYaw));
            if (delta < RETURN_DEGREES) {
                fireScreamer(level, player, initialYaw);
            } else {
                pollReturn(level, playerId, initialYaw, poll + 1);
            }
        });
    }

    /** 4) Le screamer : Hôte au visage, hurlement, écran, tension. */
    private static void fireScreamer(ServerLevel level, ServerPlayer player, float initialYaw) {
        if (player.level() != level || !contextStillValid(player)) {
            return; // tout changement de contexte pendant l'armement annule la scène
        }
        // Direction du cap initial (convention MC : yaw 0 = +Z, x = -sin, z = cos).
        double yawRad = Math.toRadians(initialYaw);
        Vec3 dir = new Vec3(-Math.sin(yawRad), 0.0, Math.cos(yawRad));
        HostEntity host = null;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = Mth.floor(player.getX() + dir.x * FACE_DIST);
        int baseZ = Mth.floor(player.getZ() + dir.z * FACE_DIST);
        int baseY = Mth.floor(player.getY());
        if (HorrorDirector.canSpawnHorrorEntityNear(player, HOST_QUOTA_RADIUS)) {
            for (int dy = 1; dy >= -2 && host == null; dy--) {
                cursor.set(baseX, baseY + dy, baseZ);
                if (!level.isLoaded(cursor)
                        || !level.getBlockState(cursor.below()).isSolidRender()
                        || !level.getBlockState(cursor).isAir()
                        || !level.getBlockState(cursor.above()).isAir()) {
                    continue;
                }
                HostEntity candidate = new HostEntity(PenumbraEntities.HOST, level);
                candidate.setOwner(player);
                candidate.snapTo(baseX + 0.5, cursor.getY(), baseZ + 0.5,
                        Mth.wrapDegrees(initialYaw + 180.0f), 0.0f); // face au joueur
                candidate.finalizeSpawn(level, level.getCurrentDifficultyAt(cursor),
                        EntitySpawnReason.EVENT, null);
                candidate.setBehavior(HostBehavior.DORMANT);
                if (!level.addFreshEntity(candidate)) {
                    candidate.discard();
                    continue;
                }
                host = candidate;
                host.getLookControl().setLookAt(player, 30.0f, 30.0f);
            }
        }
        // Le packet de screamer joue déjà le hurlement local prioritaire. Ne pas
        // envoyer un second HorrorSoundS2C, qui doublait le volume perçu.
        PenumbraNetworking.sendJumpscare(player, "screamer", 1.0f);
        if (host != null) {
            HorrorDirector.noteManifestation(player);
            HorrorDirector.noteHostObserved(player);
            UUID hostId = host.getUUID();
            HorrorDirector.runLater(level, HOST_LINGER_TICKS, () -> {
                Entity entity = level.getEntity(hostId);
                if (entity instanceof HostEntity bound && bound.isAlive()) {
                    level.sendParticles(PenumbraParticles.ASH_MOTE,
                            bound.getX(), bound.getY() + 1.3, bound.getZ(), 32, 0.4, 0.8, 0.4, 0.015);
                    bound.discard();
                }
            });
        }
    }
}
