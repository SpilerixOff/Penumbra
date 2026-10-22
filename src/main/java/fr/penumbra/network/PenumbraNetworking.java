package fr.penumbra.network;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.ScreamerHabituation;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Couche réseau de Penumbra : payloads S2C (sons d'horreur positionnés et
 * jumpscares) enregistrés en phase play, plus les helpers d'envoi côté serveur.
 * Les sons positionnés sont distribués uniquement aux joueurs assez proches
 * dans la même dimension ; l'atténuation spatiale reste gérée par le moteur
 * audio vanilla côté client.
 * Phase 4 : anti-spam sur {@link #sendSound} (au plus 1 paquet non prioritaire
 * toutes les {@value #SOUND_MIN_INTERVAL_TICKS} ticks par couple source/auditeur) ;
 * les stingers et sons de jumpscare passent par {@link #sendSoundPriority}.
 * Seul un doublon prioritaire strictement identique dans le même tick est supprimé.
 */
public final class PenumbraNetworking {

    /** Intervalle minimal entre deux sons non prioritaires d'un même joueur (ticks). */
    private static final long SOUND_MIN_INTERVAL_TICKS = 3L;
    /** Portée minimale d'un son positionné, même quand son volume est inférieur à 1. */
    private static final double MIN_AUDIBLE_RADIUS = 16.0;
    /** Portée maximale défensive afin de ne jamais diffuser un paquet à tout le niveau. */
    private static final double MAX_AUDIBLE_RADIUS = 64.0;
    /** Vanilla étend approximativement la portée de 16 blocs par unité de volume. */
    private static final double BLOCKS_PER_VOLUME = 16.0;
    /** Distance autour de la cible à laquelle un coéquipier partage un leurre lointain. */
    private static final double COMPANION_SHARE_RADIUS = 24.0;
    /** Bornes défensives avant sérialisation et à la réception client. */
    public static final float MAX_NETWORK_VOLUME = 4.0f;
    public static final float MIN_NETWORK_PITCH = 0.25f;
    public static final float MAX_NETWORK_PITCH = 4.0f;
    /** Capacité maximale de la table de throttle ; au-delà, éviction FIFO du plus ancien. */
    private static final int SOUND_THROTTLE_CAPACITY = 512;
    private static final SoundDeliveryGate SOUND_GATE =
            new SoundDeliveryGate(SOUND_THROTTLE_CAPACITY, SOUND_MIN_INTERVAL_TICKS);

    private PenumbraNetworking() {
    }

    /**
     * Son d'horreur à jouer côté client à une position précise.
     * Le client résout le {@link SoundEvent} à partir de {@code soundId}.
     */
    public record HorrorSoundS2CPacket(Identifier soundId, double x, double y, double z,
                                       float volume, float pitch) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<HorrorSoundS2CPacket> TYPE =
                new CustomPacketPayload.Type<>(PenumbraMod.id("horror_sound"));

        public static final StreamCodec<? super RegistryFriendlyByteBuf, HorrorSoundS2CPacket> CODEC =
                StreamCodec.composite(
                        Identifier.STREAM_CODEC, HorrorSoundS2CPacket::soundId,
                        ByteBufCodecs.DOUBLE, HorrorSoundS2CPacket::x,
                        ByteBufCodecs.DOUBLE, HorrorSoundS2CPacket::y,
                        ByteBufCodecs.DOUBLE, HorrorSoundS2CPacket::z,
                        ByteBufCodecs.FLOAT, HorrorSoundS2CPacket::volume,
                        ByteBufCodecs.FLOAT, HorrorSoundS2CPacket::pitch,
                        HorrorSoundS2CPacket::new);

        @Override
        public CustomPacketPayload.Type<HorrorSoundS2CPacket> type() {
            return TYPE;
        }
    }

    /**
     * Déclenchement d'un jumpscare côté client.
     * Types reconnus : {@code "sting"}, {@code "fake_creeper"},
     * {@code "apparition"}, {@code "screamer"} et le final interne
     * {@code "lunge_screamer"}.
     */
    public record JumpscareS2CPacket(String scareType, float intensity) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<JumpscareS2CPacket> TYPE =
                new CustomPacketPayload.Type<>(PenumbraMod.id("jumpscare"));

        public static final StreamCodec<? super RegistryFriendlyByteBuf, JumpscareS2CPacket> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, JumpscareS2CPacket::scareType,
                        ByteBufCodecs.FLOAT, JumpscareS2CPacket::intensity,
                        JumpscareS2CPacket::new);

        @Override
        public CustomPacketPayload.Type<JumpscareS2CPacket> type() {
            return TYPE;
        }
    }

    /**
     * Synchronisation de l'état de tension vers le client (0.5.0) : ordinal du
     * {@code TensionState} courant + tension brute 0–100. Alimente la couche
     * ambiante client (nappe sonore, voile périphérique, dérive de FOV) sans
     * jamais être affichée au joueur — l'horreur reste implicite (SPEC §4).
     */
    public record TensionSyncS2CPacket(int stateOrdinal, float tension) implements CustomPacketPayload {

        public static final CustomPacketPayload.Type<TensionSyncS2CPacket> TYPE =
                new CustomPacketPayload.Type<>(PenumbraMod.id("tension_sync"));

        public static final StreamCodec<? super RegistryFriendlyByteBuf, TensionSyncS2CPacket> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_INT, TensionSyncS2CPacket::stateOrdinal,
                        ByteBufCodecs.FLOAT, TensionSyncS2CPacket::tension,
                        TensionSyncS2CPacket::new);

        @Override
        public CustomPacketPayload.Type<TensionSyncS2CPacket> type() {
            return TYPE;
        }
    }

    /** Briefly ducks only Penumbra's ambient bed; entity audio remains spatial. */
    public record SceneHushS2CPacket(int ticks) implements CustomPacketPayload {
        public SceneHushS2CPacket { ticks = Math.max(0, Math.min(60, ticks)); }
        public static final Type<SceneHushS2CPacket> TYPE = new Type<>(PenumbraMod.id("scene_hush"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, SceneHushS2CPacket> CODEC =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, SceneHushS2CPacket::ticks, SceneHushS2CPacket::new);
        @Override public Type<SceneHushS2CPacket> type() { return TYPE; }
    }

    public static void sendSceneHush(ServerPlayer player, int ticks) {
        ServerPlayNetworking.send(player, new SceneHushS2CPacket(ticks));
    }

    /** Enregistre les payloads en phase play, sens serveur → client. */
    public static void registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().register(HorrorSoundS2CPacket.TYPE, HorrorSoundS2CPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(JumpscareS2CPacket.TYPE, JumpscareS2CPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TensionSyncS2CPacket.TYPE, TensionSyncS2CPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SceneHushS2CPacket.TYPE, SceneHushS2CPacket.CODEC);
    }

    /** Envoie l'état de tension courant à un joueur (couche ambiante client, 0.5.0). */
    public static void sendTensionSync(ServerPlayer player, int stateOrdinal, float tension) {
        float safeTension = Float.isFinite(tension)
                ? Math.max(0.0f, Math.min(100.0f, tension)) : 0.0f;
        ServerPlayNetworking.send(player, new TensionSyncS2CPacket(stateOrdinal, safeTension));
    }

    /**
     * Diffuse un son d'horreur positionné aux joueurs assez proches de
     * {@code source} dans sa dimension, sous réserve du throttle anti-spam :
     * au plus 1 paquet toutes les {@value #SOUND_MIN_INTERVAL_TICKS} ticks par
     * auditeur. Le client applique ensuite l'atténuation vanilla selon sa
     * distance réelle à {@code pos}.
     * Les stingers de poursuite et les sons accompagnant un jumpscare doivent
     * passer par {@link #sendSoundPriority}. Le stinger d'un jumpscare ne
     * transite de toute façon pas ici : il est joué en local par le client
     * ({@code JumpscareManager}) sur réception d'un {@link JumpscareS2CPacket}.
     */
    public static void sendSound(ServerPlayer source, SoundEvent sound, Vec3 pos,
                                 float volume, float pitch) {
        broadcastSound(source, null, sound, pos, volume, pitch, false, false);
    }

    /**
     * Diffuse un son d'horreur positionné hors du budget ordinaire aux auditeurs
     * proches. Un stinger ne retire aucun budget aux sons d'ambiance qui suivent ;
     * seul son doublon exact au même tick est fusionné.
     */
    public static void sendSoundPriority(ServerPlayer source, SoundEvent sound, Vec3 pos,
                                         float volume, float pitch) {
        broadcastSound(source, null, sound, pos, volume, pitch, true, false);
    }

    /**
     * Son physique strict : seuls les joueurs proches de sa vraie origine le
     * reçoivent. Il n'est ni partagé autour de la cible ni rapproché artificiellement.
     */
    public static void sendSoundPrioritySpatial(ServerPlayer source, SoundEvent sound, Vec3 pos,
                                                float volume, float pitch) {
        broadcastSound(source, null, sound, pos, volume, pitch, true, true);
    }

    /**
     * Diffuse un son aux joueurs proches sauf {@code excluded}. Sert aux sons
     * d'interface déjà joués instantanément en local par le client source
     * (clic de la caméra VHS), afin d'éviter un doublon chez lui.
     */
    public static void sendSoundNearbyExcept(ServerPlayer source, ServerPlayer excluded,
                                             SoundEvent sound, Vec3 pos,
                                             float volume, float pitch) {
        broadcastSound(source, excluded == null ? null : excluded.getUUID(),
                sound, pos, volume, pitch, false, false);
    }

    /** Variante prioritaire de {@link #sendSoundNearbyExcept}. */
    public static void sendSoundPriorityNearbyExcept(ServerPlayer source, ServerPlayer excluded,
                                                     SoundEvent sound, Vec3 pos,
                                                     float volume, float pitch) {
        broadcastSound(source, excluded == null ? null : excluded.getUUID(),
                sound, pos, volume, pitch, true, false);
    }

    /**
     * Envoi réellement individuel, conservé pour les couches psychologiques
     * non spatiales déjà réparties explicitement par le directeur.
     */
    public static void sendSoundDirect(ServerPlayer listener, SoundEvent sound, Vec3 pos,
                                       float volume, float pitch) {
        sendSoundDirect(listener, listener, sound, pos, volume, pitch);
    }

    /** An individual physical impact keeps its origin and bypasses the ambient budget. */
    public static void sendSoundDirectPriority(ServerPlayer listener, SoundEvent sound, Vec3 pos,
                                               float volume, float pitch) {
        SoundParameters parameters = sanitizeSound(sound, pos, volume, pitch);
        if (parameters == null) return;
        if (SOUND_GATE.tryAcquire(listener.level().dimension().identifier(), listener.getUUID(),
                listener.getUUID(), sound.location(), pos, parameters.volume(), parameters.pitch(),
                listener.level().getGameTime(), true)) {
            sendPacket(listener, sound, pos, parameters.volume(), parameters.pitch(), true);
        }
    }

    /** Son individuel dont la vraie source reste distincte de l'auditeur. */
    public static void sendSoundDirect(ServerPlayer source, ServerPlayer listener,
                                       SoundEvent sound, Vec3 pos,
                                       float volume, float pitch) {
        if (source.level() != listener.level()) {
            return;
        }
        SoundParameters parameters = sanitizeSound(sound, pos, volume, pitch);
        if (parameters == null) {
            return;
        }
        ServerLevel level = listener.level();
        if (SOUND_GATE.tryAcquire(level.dimension().identifier(), source.getUUID(),
                listener.getUUID(), sound.location(), pos,
                parameters.volume(), parameters.pitch(), level.getGameTime(), false)) {
            sendPacket(listener, sound, pos, parameters.volume(), parameters.pitch());
        }
    }

    private static void broadcastSound(ServerPlayer source, UUID excluded,
                                       SoundEvent sound, Vec3 pos,
                                       float volume, float pitch, boolean priority,
                                       boolean spatialOnly) {
        if (!(source.level() instanceof ServerLevel level)) {
            return;
        }
        SoundParameters parameters = sanitizeSound(sound, pos, volume, pitch);
        if (parameters == null) {
            return;
        }
        long gameTime = level.getGameTime();
        Vec3 sourcePos = source.position();
        for (ServerPlayer listener : level.players()) {
            if (excluded != null && excluded.equals(listener.getUUID())) {
                continue;
            }
            /*
             * Un auditeur reçoit le son s'il est proche de sa position réelle
             * OU du joueur auquel l'événement est arrivé. Ce second critère est
             * essentiel pour les leurres lointains : la cible et son coéquipier
             * côte à côte doivent entendre le même événement, sans le diffuser
             * à tout le niveau.
            */
            Vec3 listenerPos = listener.position();
            boolean inRange = spatialOnly
                    ? shouldReceiveSpatialSound(listenerPos, pos, parameters.volume())
                    : shouldReceiveSound(listenerPos, sourcePos, pos, parameters.volume());
            if (!inRange) {
                continue;
            }
            boolean accepted = SOUND_GATE.tryAcquire(level.dimension().identifier(),
                    source.getUUID(), listener.getUUID(), sound.location(), pos,
                    parameters.volume(), parameters.pitch(), gameTime, priority);
            if (!accepted) {
                if (PenumbraConfig.get().debugLogging) {
                    PenumbraMod.LOGGER.info(
                            "[Penumbra] Son {} supprimé (anti-spam/doublon) pour {}",
                            sound.location(), listener.getGameProfile().name());
                }
                continue;
            }
            sendPacket(listener, sound, pos, parameters.volume(), parameters.pitch(), spatialOnly);
        }
    }

    private static void sendPacket(ServerPlayer listener, SoundEvent sound, Vec3 pos,
                                   float volume, float pitch) {
        sendPacket(listener, sound, pos, volume, pitch, false);
    }

    private static void sendPacket(ServerPlayer listener, SoundEvent sound, Vec3 pos,
                                   float volume, float pitch, boolean preserveOrigin) {
        Vec3 audiblePos = preserveOrigin
                ? pos : spatializeForListener(listener.position(), pos, volume);
        ServerPlayNetworking.send(listener,
                new HorrorSoundS2CPacket(sound.location(), audiblePos.x, audiblePos.y, audiblePos.z,
                        volume, pitch));
    }

    /**
     * Conserve la direction d'un leurre lointain mais rapproche sa position
     * acoustique jusqu'à 75 % de la portée vanilla. Le volume reste inchangé :
     * le son demeure discret, tout en étant réellement audible par la cible et
     * les joueurs placés près d'elle.
     */
    static Vec3 spatializeForListener(Vec3 listenerPos, Vec3 soundPos, float volume) {
        double vanillaRadius = Math.max(1.0, Math.max(0.0f, volume)) * BLOCKS_PER_VOLUME;
        double audibleDistance = vanillaRadius * 0.75;
        Vec3 delta = soundPos.subtract(listenerPos);
        if (delta.lengthSqr() <= audibleDistance * audibleDistance || delta.lengthSqr() < 1.0e-8) {
            return soundPos;
        }
        return listenerPos.add(delta.normalize().scale(audibleDistance));
    }

    /** Portée réseau cohérente avec l'atténuation vanilla, exposée aux tests. */
    static double audibleRadius(float volume) {
        float safeVolume = Float.isFinite(volume)
                ? Math.max(0.0f, Math.min(MAX_NETWORK_VOLUME, volume)) : 0.0f;
        double requested = Math.max(1.0, safeVolume) * BLOCKS_PER_VOLUME;
        return Math.max(MIN_AUDIBLE_RADIUS, Math.min(MAX_AUDIBLE_RADIUS, requested));
    }

    /** Vrai si un auditeur est proche du son ou réellement à côté de sa cible. */
    static boolean shouldReceiveSound(Vec3 listener, Vec3 source, Vec3 sound, float volume) {
        double radius = audibleRadius(volume);
        return listener.distanceToSqr(sound) <= radius * radius
                || listener.distanceToSqr(source) <= COMPANION_SHARE_RADIUS * COMPANION_SHARE_RADIUS;
    }

    static boolean shouldReceiveSpatialSound(Vec3 listener, Vec3 sound, float volume) {
        double radius = audibleRadius(volume);
        return listener.distanceToSqr(sound) <= radius * radius;
    }

    /** Filtre NaN/infini/zéro et borne les paramètres envoyés aux clients. */
    static SoundParameters sanitizeSound(SoundEvent sound, Vec3 pos, float volume, float pitch) {
        if (sound == null || pos == null
                || !Double.isFinite(pos.x) || !Double.isFinite(pos.y) || !Double.isFinite(pos.z)
                || !Float.isFinite(volume) || volume <= 0.0f
                || !Float.isFinite(pitch) || pitch <= 0.0f) {
            return null;
        }
        return new SoundParameters(
                Math.min(MAX_NETWORK_VOLUME, volume),
                Math.max(MIN_NETWORK_PITCH, Math.min(MAX_NETWORK_PITCH, pitch)));
    }

    record SoundParameters(float volume, float pitch) {
    }

    /**
     * Envoie l'effet visuel au joueur ciblé. Le son principal reste joué
     * instantanément en local chez lui, puis il est reproduit spatialement pour
     * les autres joueurs assez proches, sans doublon chez la cible.
     */
    public static void sendJumpscare(ServerPlayer player, String scareType, float intensity) {
        PenumbraConfig config = PenumbraConfig.get();
        float clamped = sanitizeJumpscareIntensity(intensity);
        if (config.jumpscareFrequency <= 0.0f || clamped <= 0.0f
                || !isKnownJumpscare(scareType)) {
            return;
        }

        // 0.14.0 — anti-habituation : les visages plein écran récents dévaluent
        // le suivant (décote d'intensité) et peuvent le convertir en refus
        // sensibilisant. Les finales de contact ne sont jamais refusées.
        if (config.screamerHabituationEnabled && config.screamersEnabled
                && isScreamerFace(scareType)) {
            long gameTime = player.level().getGameTime();
            boolean contact = isContactFinal(scareType);
            ScreamerHabituation.Decision decision = ScreamerHabituation.decide(
                    player.getUUID(), gameTime, contact, player.getRandom());
            if (decision.deny()) {
                // L'attente déçue : un souffle discret à la place du cri promis.
                ServerPlayNetworking.send(player, new JumpscareS2CPacket(
                        "apparition", Math.min(0.6f, clamped * 0.6f)));
                sendSoundPriorityNearbyExcept(player, player,
                        PenumbraSounds.HOST_WHISPER, player.position(),
                        0.5f * clamped, 0.9f);
                return;
            }
            clamped = sanitizeJumpscareIntensity(clamped * decision.intensityMultiplier());
            if (clamped <= 0.0f) {
                return;
            }
            ScreamerHabituation.recordFace(player.getUUID(), gameTime);
        }
        ServerPlayNetworking.send(player, new JumpscareS2CPacket(scareType, clamped));
        if ("impact".equals(scareType)) return; // Entity already emitted its physical sound.
        SoundEvent nearbySound;
        float volume = clamped;
        float pitch = 1.0f;
        switch (scareType) {
            case "sting" -> nearbySound = PenumbraSounds.HOST_STINGER;
            case "fake_creeper" -> nearbySound = PenumbraSounds.FAKE_CREEPER;
            case "apparition" -> {
                nearbySound = PenumbraSounds.HOST_WHISPER;
                volume = Math.min(1.0f, config.jumpscareVolume * clamped * 0.7f);
                pitch = 0.9f;
            }
            case "screamer", "lunge_screamer", "rampant_screamer",
                    "absent_screamer", "chorus_screamer", "refuge_screamer",
                    "mimic_screamer", "listener_screamer" -> {
                nearbySound = config.screamersEnabled
                        ? PenumbraSounds.SCREAMER_SHRIEK : PenumbraSounds.HOST_STINGER;
                // Le client auditeur applique son plafond jumpscareVolume une seule
                // fois à ce SoundEvent ; ne pas le pré-multiplier ici.
                volume = clamped;
                if (!"screamer".equals(scareType)) {
                    pitch = 0.92f;
                }
            }
            default -> throw new IllegalStateException("Type filtré mais non traité : " + scareType);
        }
        sendSoundPriorityNearbyExcept(
                player, player, nearbySound, player.position(), volume, pitch);
    }

    static boolean isKnownJumpscare(String scareType) {
        return "impact".equals(scareType) || "sting".equals(scareType) || "fake_creeper".equals(scareType)
                || "apparition".equals(scareType) || "screamer".equals(scareType)
                || "lunge_screamer".equals(scareType)
                || "rampant_screamer".equals(scareType)
                || "absent_screamer".equals(scareType)
                || "chorus_screamer".equals(scareType)
                || "refuge_screamer".equals(scareType)
                || "mimic_screamer".equals(scareType) || "listener_screamer".equals(scareType);
    }

    static float sanitizeJumpscareIntensity(float intensity) {
        return Float.isFinite(intensity) ? Math.max(0.0f, Math.min(1.0f, intensity)) : 0.0f;
    }

    /** Types qui affichent un visage plein écran, soumis à l'anti-habituation. */
    static boolean isScreamerFace(String scareType) {
        return "screamer".equals(scareType)
                || "lunge_screamer".equals(scareType)
                || "rampant_screamer".equals(scareType)
                || "absent_screamer".equals(scareType)
                || "chorus_screamer".equals(scareType)
                || "refuge_screamer".equals(scareType)
                || "mimic_screamer".equals(scareType);
    }

    /**
     * Finales déclenchées par le contact physique d'une entité : refuser
     * l'image ferait paraître le coup cassé, seule la décote s'applique.
     */
    static boolean isContactFinal(String scareType) {
        return "lunge_screamer".equals(scareType)
                || "rampant_screamer".equals(scareType)
                || "absent_screamer".equals(scareType);
    }

    /** Oublie le throttle d'un joueur déconnecté. */
    public static void clear(UUID playerId) {
        SOUND_GATE.clear(playerId);
        ScreamerHabituation.forget(playerId);
    }

    /** Purge l'état statique entre deux serveurs ouverts dans la même JVM. */
    public static void onServerStopped() {
        SOUND_GATE.clearAll();
        ScreamerHabituation.clearAll();
    }
}
