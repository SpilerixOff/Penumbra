package fr.penumbra.network;

import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.director.sequence.LinkedSceneManager;
import fr.penumbra.entity.fauxmob.FauxMobManager;
import fr.penumbra.entity.projectionist.ProjectionistEntity;
import fr.penumbra.story.ConfrontationManager;
import fr.penumbra.photo.PhotoAlbumState;
import fr.penumbra.photo.PhotoContent;
import fr.penumbra.photo.PhotoData;
import fr.penumbra.photo.PhotoEvidence;
import fr.penumbra.photo.PhotoEvidenceDetector;
import fr.penumbra.photo.PhotoEvidenceRules;
import fr.penumbra.photo.PhotoFrameEntity;
import fr.penumbra.photo.investigation.InvestigationClue;
import fr.penumbra.photo.investigation.InvestigationRules;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Protocole borné de la caméra : capture, chargement à la demande et flash partagé. */
public final class PhotoNetworking {

    public static final long CAPTURE_COOLDOWN_TICKS = 40L;
    public static final long CAPTURE_TICKET_TTL_TICKS = 60L;
    public static final double FLASH_RADIUS = 64.0;
    /** Une capture sur environ sept, tirage effectué exclusivement par le serveur. */
    public static final float PROJECTIONIST_PHOTO_CHANCE = 0.15f;
    /** Une manifestation maximum par joueur toutes les 90 secondes. */
    public static final long PROJECTIONIST_COOLDOWN_TICKS = 1_800L;
    private static final double FRAME_VIEW_RADIUS = 160.0;
    private static final long REQUEST_WINDOW_TICKS = 20L;
    private static final int REQUESTS_PER_WINDOW = 4;
    public static final int ALBUM_PAGE_SIZE = 6;
    public static final byte ALBUM_SCOPE_OWNED = 0;
    public static final byte ALBUM_SCOPE_PUBLIC = 1;
    public static final byte MUTATION_RENAME = 0;
    public static final byte MUTATION_DELETE = 1;
    public static final byte MUTATION_VISIBILITY = 2;
    public static final byte MUTATION_IMPORTANT = 3;
    private static final Map<UUID, Long> LAST_CAPTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingCapture> PENDING_CAPTURES = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_PROJECTIONIST_MANIFESTATIONS =
            new ConcurrentHashMap<>();
    private static final PhotoRequestRateLimiter REQUEST_LIMITER =
            new PhotoRequestRateLimiter(REQUEST_WINDOW_TICKS, REQUESTS_PER_WINDOW);
    private static final PhotoRequestRateLimiter ANALYSIS_REQUEST_LIMITER =
            new PhotoRequestRateLimiter(REQUEST_WINDOW_TICKS, 6);
    private static final PhotoRequestRateLimiter ALBUM_REQUEST_LIMITER =
            new PhotoRequestRateLimiter(REQUEST_WINDOW_TICKS, 4);
    private static final PhotoRequestRateLimiter MUTATION_LIMITER =
            new PhotoRequestRateLimiter(REQUEST_WINDOW_TICKS, 4);

    private PhotoNetworking() {
    }

    /** Fige le regard et les anomalies au véritable instant du flash. */
    public record PhotoShutterC2S(long sequence, float zoomFactor) implements CustomPacketPayload {
        public static final Type<PhotoShutterC2S> TYPE =
                new Type<>(PenumbraMod.id("photo_shutter"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoShutterC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, PhotoShutterC2S::sequence,
                        ByteBufCodecs.FLOAT, PhotoShutterC2S::zoomFactor,
                        PhotoShutterC2S::new);

        @Override
        public Type<PhotoShutterC2S> type() {
            return TYPE;
        }
    }

    /** Accusé serveur : les pixels ne quittent le client qu'après acceptation du flash. */
    public record PhotoShutterResultS2C(long sequence, boolean accepted)
            implements CustomPacketPayload {
        public static final Type<PhotoShutterResultS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_shutter_result"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoShutterResultS2C> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, PhotoShutterResultS2C::sequence,
                        ByteBufCodecs.BOOL, PhotoShutterResultS2C::accepted,
                        PhotoShutterResultS2C::new);

        @Override
        public Type<PhotoShutterResultS2C> type() {
            return TYPE;
        }
    }

    /** Les pixels ne sont acceptés qu'avec le ticket de flash correspondant. */
    public record PhotoCaptureC2S(long sequence, byte[] pixels) implements CustomPacketPayload {
        public static final Type<PhotoCaptureC2S> TYPE =
                new Type<>(PenumbraMod.id("photo_capture"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoCaptureC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, PhotoCaptureC2S::sequence,
                        ByteBufCodecs.byteArray(PhotoAlbumState.RGB_BYTES), PhotoCaptureC2S::pixels,
                        PhotoCaptureC2S::new);

        public PhotoCaptureC2S {
            pixels = pixels == null ? new byte[0] : pixels.clone();
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }

        @Override
        public Type<PhotoCaptureC2S> type() {
            return TYPE;
        }
    }

    public record PhotoRequestC2S(UUID photoId) implements CustomPacketPayload {
        public static final Type<PhotoRequestC2S> TYPE =
                new Type<>(PenumbraMod.id("photo_request"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoRequestC2S> CODEC =
                StreamCodec.composite(UUIDUtil.STREAM_CODEC, PhotoRequestC2S::photoId, PhotoRequestC2S::new);

        @Override
        public Type<PhotoRequestC2S> type() {
            return TYPE;
        }
    }

    /** Demande uniquement le verdict d'une émulsion arrivée à maturité. */
    public record PhotoAnalysisRequestC2S(UUID photoId) implements CustomPacketPayload {
        public static final Type<PhotoAnalysisRequestC2S> TYPE =
                new Type<>(PenumbraMod.id("photo_analysis_request"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoAnalysisRequestC2S> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, PhotoAnalysisRequestC2S::photoId,
                        PhotoAnalysisRequestC2S::new);

        @Override
        public Type<PhotoAnalysisRequestC2S> type() {
            return TYPE;
        }
    }

    /** Demande une page de métadonnées, jamais les pixels de toute la galerie. */
    public record AlbumPageRequestC2S(byte scope, int page) implements CustomPacketPayload {
        public static final Type<AlbumPageRequestC2S> TYPE =
                new Type<>(PenumbraMod.id("photo_album_page_request"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, AlbumPageRequestC2S> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BYTE, AlbumPageRequestC2S::scope,
                        ByteBufCodecs.VAR_INT, AlbumPageRequestC2S::page,
                        AlbumPageRequestC2S::new);

        @Override
        public Type<AlbumPageRequestC2S> type() {
            return TYPE;
        }
    }

    public record AlbumEntry(UUID photoId, UUID owner, long capturedAtTick,
                             boolean projectionist, String title,
                             boolean publicVisible, boolean important) {
        private static final StreamCodec<RegistryFriendlyByteBuf, AlbumEntry> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, AlbumEntry::photoId,
                        UUIDUtil.STREAM_CODEC, AlbumEntry::owner,
                        ByteBufCodecs.VAR_LONG, AlbumEntry::capturedAtTick,
                        ByteBufCodecs.BOOL, AlbumEntry::projectionist,
                        ByteBufCodecs.stringUtf8(PhotoAlbumState.MAX_TITLE_LENGTH), AlbumEntry::title,
                        ByteBufCodecs.BOOL, AlbumEntry::publicVisible,
                        ByteBufCodecs.BOOL, AlbumEntry::important,
                        AlbumEntry::new);

        public AlbumEntry {
            title = PhotoAlbumState.sanitizeTitle(title);
        }
    }

    public record AlbumPageS2C(byte scope, int page, int totalEntries,
                               int dossierStage, int dossierDiscovered,
                               List<AlbumEntry> entries) implements CustomPacketPayload {
        public static final Type<AlbumPageS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_album_page"));
        private static final StreamCodec<RegistryFriendlyByteBuf, List<AlbumEntry>> ENTRIES_CODEC =
                AlbumEntry.CODEC.apply(ByteBufCodecs.list(PhotoAlbumState.MAX_PAGE_SIZE));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, AlbumPageS2C> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.BYTE, AlbumPageS2C::scope,
                        ByteBufCodecs.VAR_INT, AlbumPageS2C::page,
                        ByteBufCodecs.VAR_INT, AlbumPageS2C::totalEntries,
                        ByteBufCodecs.VAR_INT, AlbumPageS2C::dossierStage,
                        ByteBufCodecs.VAR_INT, AlbumPageS2C::dossierDiscovered,
                        ENTRIES_CODEC, AlbumPageS2C::entries,
                        AlbumPageS2C::new);

        public AlbumPageS2C {
            page = Math.max(0, page);
            totalEntries = Math.max(0, totalEntries);
            dossierStage = Math.max(0, Math.min(4, dossierStage));
            dossierDiscovered = Math.max(0, Math.min(
                    PhotoEvidence.discoverableCount(), dossierDiscovered));
            entries = List.copyOf(entries);
        }

        @Override
        public Type<AlbumPageS2C> type() {
            return TYPE;
        }
    }

    /** Mutation unique et bornée ; l'action et la propriété sont revalidées serveur. */
    public record AlbumMutationC2S(UUID photoId, byte action, String value)
            implements CustomPacketPayload {
        public static final Type<AlbumMutationC2S> TYPE =
                new Type<>(PenumbraMod.id("photo_album_mutation"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, AlbumMutationC2S> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, AlbumMutationC2S::photoId,
                        ByteBufCodecs.BYTE, AlbumMutationC2S::action,
                        ByteBufCodecs.stringUtf8(PhotoAlbumState.MAX_TITLE_LENGTH),
                        AlbumMutationC2S::value,
                        AlbumMutationC2S::new);

        public AlbumMutationC2S {
            value = value == null ? "" : value;
        }

        @Override
        public Type<AlbumMutationC2S> type() {
            return TYPE;
        }
    }

    public record AlbumMutationResultS2C(UUID photoId, byte action, boolean success)
            implements CustomPacketPayload {
        public static final Type<AlbumMutationResultS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_album_mutation_result"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, AlbumMutationResultS2C> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, AlbumMutationResultS2C::photoId,
                        ByteBufCodecs.BYTE, AlbumMutationResultS2C::action,
                        ByteBufCodecs.BOOL, AlbumMutationResultS2C::success,
                        AlbumMutationResultS2C::new);

        @Override
        public Type<AlbumMutationResultS2C> type() {
            return TYPE;
        }
    }

    /** Invalide immédiatement une texture supprimée chez tous les clients. */
    public record PhotoRemovedS2C(UUID photoId) implements CustomPacketPayload {
        public static final Type<PhotoRemovedS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_removed"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoRemovedS2C> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, PhotoRemovedS2C::photoId,
                        PhotoRemovedS2C::new);

        @Override
        public Type<PhotoRemovedS2C> type() {
            return TYPE;
        }
    }

    public record PhotoDataS2C(UUID photoId, long capturedAtTick, byte[] pixels,
                               boolean projectionist, boolean absentTrueForm,
                               boolean evidenceRevealed,
                               List<String> evidenceIds)
            implements CustomPacketPayload {
        public static final Type<PhotoDataS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_data"));
        private static final StreamCodec<? super RegistryFriendlyByteBuf, List<String>> EVIDENCE_CODEC =
                ByteBufCodecs.stringUtf8(PhotoEvidence.MAX_ID_LENGTH)
                        .apply(ByteBufCodecs.list(PhotoEvidence.MAX_PER_PHOTO));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoDataS2C> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, PhotoDataS2C::photoId,
                        ByteBufCodecs.VAR_LONG, PhotoDataS2C::capturedAtTick,
                        ByteBufCodecs.byteArray(PhotoAlbumState.RGB_BYTES), PhotoDataS2C::pixels,
                        ByteBufCodecs.BOOL, PhotoDataS2C::projectionist,
                        ByteBufCodecs.BOOL, PhotoDataS2C::absentTrueForm,
                        ByteBufCodecs.BOOL, PhotoDataS2C::evidenceRevealed,
                        EVIDENCE_CODEC, PhotoDataS2C::evidenceIds,
                        PhotoDataS2C::new);

        public PhotoDataS2C {
            pixels = pixels == null ? new byte[0] : pixels.clone();
            evidenceIds = evidenceRevealed
                    ? normalizeEvidenceIds(evidenceIds, projectionist) : List.of();
        }

        public PhotoDataS2C(UUID photoId, long capturedAtTick, byte[] pixels) {
            this(photoId, capturedAtTick, pixels, false, false, false, List.of());
        }

        public PhotoDataS2C(UUID photoId, long capturedAtTick, byte[] pixels,
                            boolean projectionist) {
            this(photoId, capturedAtTick, pixels, projectionist, false, false, List.of());
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }

        @Override
        public Type<PhotoDataS2C> type() {
            return TYPE;
        }
    }

    /** Verdict léger ; même une liste vide confirme « aucune anomalie ». */
    public record PhotoAnalysisS2C(UUID photoId, List<String> evidenceIds)
            implements CustomPacketPayload {
        public static final Type<PhotoAnalysisS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_analysis"));
        private static final StreamCodec<? super RegistryFriendlyByteBuf, List<String>> EVIDENCE_CODEC =
                ByteBufCodecs.stringUtf8(PhotoEvidence.MAX_ID_LENGTH)
                        .apply(ByteBufCodecs.list(PhotoEvidence.MAX_PER_PHOTO));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoAnalysisS2C> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, PhotoAnalysisS2C::photoId,
                        EVIDENCE_CODEC, PhotoAnalysisS2C::evidenceIds,
                        PhotoAnalysisS2C::new);

        public PhotoAnalysisS2C {
            evidenceIds = normalizeEvidenceIds(evidenceIds, false);
        }

        @Override
        public Type<PhotoAnalysisS2C> type() {
            return TYPE;
        }
    }

    /** Envoyé après 1,5 s d'observation volontaire d'un négatif hanté développé. */
    public record PhotoObserveC2S(UUID photoId) implements CustomPacketPayload {
        public static final Type<PhotoObserveC2S> TYPE =
                new Type<>(PenumbraMod.id("photo_observe"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoObserveC2S> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, PhotoObserveC2S::photoId, PhotoObserveC2S::new);

        @Override
        public Type<PhotoObserveC2S> type() {
            return TYPE;
        }
    }

    /** Ferme la visionneuse seulement si le serveur a réellement créé l'apparition. */
    public record PhotoManifestS2C(UUID photoId) implements CustomPacketPayload {
        public static final Type<PhotoManifestS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_manifest"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoManifestS2C> CODEC =
                StreamCodec.composite(
                        UUIDUtil.STREAM_CODEC, PhotoManifestS2C::photoId, PhotoManifestS2C::new);

        @Override
        public Type<PhotoManifestS2C> type() {
            return TYPE;
        }
    }

    public record PhotoFlashS2C(double x, double y, double z) implements CustomPacketPayload {
        public static final Type<PhotoFlashS2C> TYPE =
                new Type<>(PenumbraMod.id("photo_flash"));
        public static final StreamCodec<? super RegistryFriendlyByteBuf, PhotoFlashS2C> CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.DOUBLE, PhotoFlashS2C::x,
                        ByteBufCodecs.DOUBLE, PhotoFlashS2C::y,
                        ByteBufCodecs.DOUBLE, PhotoFlashS2C::z,
                        PhotoFlashS2C::new);

        @Override
        public Type<PhotoFlashS2C> type() {
            return TYPE;
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.serverboundPlay().register(PhotoShutterC2S.TYPE, PhotoShutterC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhotoCaptureC2S.TYPE, PhotoCaptureC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhotoRequestC2S.TYPE, PhotoRequestC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                PhotoAnalysisRequestC2S.TYPE, PhotoAnalysisRequestC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(PhotoObserveC2S.TYPE, PhotoObserveC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                AlbumPageRequestC2S.TYPE, AlbumPageRequestC2S.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(
                AlbumMutationC2S.TYPE, AlbumMutationC2S.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PhotoDataS2C.TYPE, PhotoDataS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                PhotoShutterResultS2C.TYPE, PhotoShutterResultS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PhotoAnalysisS2C.TYPE, PhotoAnalysisS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PhotoFlashS2C.TYPE, PhotoFlashS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PhotoManifestS2C.TYPE, PhotoManifestS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(AlbumPageS2C.TYPE, AlbumPageS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(
                AlbumMutationResultS2C.TYPE, AlbumMutationResultS2C.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(PhotoRemovedS2C.TYPE, PhotoRemovedS2C.CODEC);
    }

    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(PhotoShutterC2S.TYPE,
                (payload, context) -> acceptShutter(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PhotoCaptureC2S.TYPE,
                (payload, context) -> acceptCapture(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(PhotoRequestC2S.TYPE,
                (payload, context) -> answerRequest(context.player(), payload.photoId()));
        ServerPlayNetworking.registerGlobalReceiver(PhotoAnalysisRequestC2S.TYPE,
                (payload, context) -> answerAnalysis(context.player(), payload.photoId()));
        ServerPlayNetworking.registerGlobalReceiver(PhotoObserveC2S.TYPE,
                (payload, context) -> acceptObservation(context.player(), payload.photoId()));
        ServerPlayNetworking.registerGlobalReceiver(AlbumPageRequestC2S.TYPE,
                (payload, context) -> answerAlbumPage(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(AlbumMutationC2S.TYPE,
                (payload, context) -> acceptAlbumMutation(context.player(), payload));
    }

    private static void acceptShutter(ServerPlayer player, PhotoShutterC2S payload) {
        if (!PenumbraC2S.allowC2S(player)) {
            return;
        }
        long now = player.level().getGameTime();
        Long last = LAST_CAPTURES.get(player.getUUID());
        if (!player.isAlive()
                || player.isSpectator()
                || player.getMainHandItem().getItem() != PenumbraItems.BINOCULARS
                || last != null && now >= last && now - last < CAPTURE_COOLDOWN_TICKS) {
            ServerPlayNetworking.send(player,
                    new PhotoShutterResultS2C(payload.sequence(), false));
            return;
        }
        LAST_CAPTURES.put(player.getUUID(), now);
        boolean projectionist = player.getRandom().nextFloat() < PROJECTIONIST_PHOTO_CHANCE;
        float zoomFactor = PhotoEvidenceRules.sanitizeZoomFactor(payload.zoomFactor());
        List<PhotoEvidence> evidence = PhotoEvidenceDetector.detect(player, zoomFactor);
        PENDING_CAPTURES.put(player.getUUID(),
                new PendingCapture(payload.sequence(), now, projectionist, evidence));
        ServerPlayNetworking.send(player,
                new PhotoShutterResultS2C(payload.sequence(), true));
        for (PhotoEvidenceDetector.AbsentFlashTarget target
                : PhotoEvidenceDetector.detectAbsentFlashTargets(player, zoomFactor)) {
            // 1.0 : le double de la Confrontation a sa propre règle de flash.
            if (ConfrontationManager.isDuelDouble(target.absent().getUUID())) {
                continue;
            }
            boolean privatePerception = target.absent().isFalseCompanion();
            if (!privatePerception && !PenumbraConfig.get().absentCameraCounterplayEnabled) {
                continue;
            }
            if (target.absent().onCameraFlash(player, target.photographed())) {
                // Le faux compagnon joue déjà sa disparition via un payload
                // direct. Seul L'Absent physique doit être entendu à proximité.
                if (!privatePerception) {
                    PenumbraNetworking.sendSoundPrioritySpatial(
                            player, PenumbraSounds.TORCH_SPUTTER,
                            target.absent().position(),
                            target.photographed() ? 0.85f : 0.58f,
                            target.photographed() ? 0.54f : 0.68f);
                }
            }
        }
        LinkedSceneManager.onCameraFlash(player);
        FauxMobManager.onCameraFlash(player);
        ConfrontationManager.onCameraFlash(player);
        broadcastFlash(player);
    }

    private static void acceptCapture(ServerPlayer player, PhotoCaptureC2S payload) {
        if (!PenumbraC2S.allowC2S(player)) {
            return;
        }
        UUID playerId = player.getUUID();
        PendingCapture pending = PENDING_CAPTURES.get(playerId);
        long now = player.level().getGameTime();
        if (pending == null || pending.sequence() != payload.sequence()
                || now < pending.capturedAtTick()
                || now - pending.capturedAtTick() > CAPTURE_TICKET_TTL_TICKS) {
            if (pending != null && now >= pending.capturedAtTick()
                    && now - pending.capturedAtTick() > CAPTURE_TICKET_TTL_TICKS) {
                PENDING_CAPTURES.remove(playerId, pending);
            }
            return;
        }
        // Consommation atomique : le même tableau de pixels ne peut jamais être rejoué.
        if (!PENDING_CAPTURES.remove(playerId, pending)) {
            return;
        }
        byte[] pixels = payload.pixels();
        if (pixels.length != PhotoAlbumState.RGB_BYTES
                || !player.isAlive() || player.isSpectator()) {
            return;
        }
        PhotoAlbumState.StoredPhoto stored = PhotoAlbumState.get(player.level().getServer())
                .add(playerId, pending.capturedAtTick(), pixels,
                        pending.projectionist(), pending.evidence())
                .orElse(null);
        if (stored == null) {
            player.sendOverlayMessage(Component.translatable("message.penumbra.photo.album_full"));
            return;
        }

        PhotoData reference = new PhotoData(
                stored.id(), stored.capturedAtTick(), stored.projectionist());
        ItemStack photo = PhotoContent.createPhoto(reference, now);
        if (!player.getInventory().add(photo)) {
            player.drop(photo, false);
            player.sendOverlayMessage(Component.translatable("message.penumbra.photo.inventory_full"));
        } else {
            player.sendOverlayMessage(Component.translatable("message.penumbra.photo.captured"));
        }
        ServerPlayNetworking.send(player,
                new PhotoDataS2C(stored.id(), stored.capturedAtTick(),
                        stored.pixels(), stored.projectionist(),
                        stored.evidence().contains(PhotoEvidence.ABSENT), false, List.of()));
    }

    private static void answerRequest(ServerPlayer player, UUID photoId) {
        if (photoId == null || !PenumbraC2S.allowC2S(player) || !allowPhotoRequest(player)) {
            return;
        }
        PhotoAlbumState.StoredPhoto stored = PhotoAlbumState.get(player.level().getServer())
                .find(photoId).orElse(null);
        if (stored == null || !mayView(player, stored)) {
            return;
        }
        boolean revealed = PhotoEvidenceRules.isDeveloped(
                player.level().getGameTime(), stored.capturedAtTick(), PhotoData.DEVELOPMENT_TICKS);
        if (revealed && stored.owner().equals(player.getUUID())) {
            notifyDossierDiscovery(player, PhotoAlbumState.get(player.level().getServer())
                    .recordEvidence(player.getUUID(), stored.evidence()));
        }
        ServerPlayNetworking.send(player,
                new PhotoDataS2C(stored.id(), stored.capturedAtTick(),
                        stored.pixels(), stored.projectionist(),
                        stored.evidence().contains(PhotoEvidence.ABSENT), revealed,
                        revealed ? evidenceIds(stored) : List.of()));
    }

    private static void answerAnalysis(ServerPlayer player, UUID photoId) {
        if (photoId == null || !PenumbraC2S.allowC2S(player)
                || !ANALYSIS_REQUEST_LIMITER.allow(
                player.getUUID(), player.level().getGameTime())) {
            return;
        }
        PhotoAlbumState album = PhotoAlbumState.get(player.level().getServer());
        PhotoAlbumState.StoredPhoto stored = album.find(photoId).orElse(null);
        long now = player.level().getGameTime();
        if (stored == null || !mayView(player, stored)
                || !PhotoEvidenceRules.isDeveloped(
                now, stored.capturedAtTick(), PhotoData.DEVELOPMENT_TICKS)) {
            return;
        }
        if (stored.owner().equals(player.getUUID())) {
            notifyDossierDiscovery(player,
                    album.recordEvidence(player.getUUID(), stored.evidence()));
        }
        ServerPlayNetworking.send(player,
                new PhotoAnalysisS2C(stored.id(), evidenceIds(stored)));
    }

    private static void answerAlbumPage(ServerPlayer player, AlbumPageRequestC2S payload) {
        if (!PenumbraC2S.allowC2S(player) || !holdsCamera(player)
                || !ALBUM_REQUEST_LIMITER.allow(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        PhotoAlbumState.AlbumScope scope = switch (payload.scope()) {
            case ALBUM_SCOPE_OWNED -> PhotoAlbumState.AlbumScope.OWNED;
            case ALBUM_SCOPE_PUBLIC -> PhotoAlbumState.AlbumScope.PUBLIC;
            default -> null;
        };
        if (scope == null || payload.page() < 0) {
            return;
        }
        PhotoAlbumState album = PhotoAlbumState.get(player.level().getServer());
        PhotoAlbumState.EvidenceDiscovery discovery = album.revealDeveloped(
                player.getUUID(), player.level().getGameTime(), PhotoData.DEVELOPMENT_TICKS);
        notifyDossierDiscovery(player, discovery);
        PhotoAlbumState.DossierProgress dossier = album.dossier(player.getUUID());
        PhotoAlbumState.AlbumPage page = album.page(
                player.getUUID(), scope, payload.page(), ALBUM_PAGE_SIZE);
        List<AlbumEntry> entries = page.entries().stream()
                .map(entry -> new AlbumEntry(entry.id(), entry.owner(), entry.capturedAtTick(),
                        entry.projectionist(), entry.title(), entry.publicVisible(),
                        entry.important()))
                .toList();
        ServerPlayNetworking.send(player,
                new AlbumPageS2C(payload.scope(), page.page(), page.totalEntries(),
                        dossier.stage(), dossier.count(), entries));
    }

    private static void acceptAlbumMutation(ServerPlayer player, AlbumMutationC2S payload) {
        if (payload.photoId() == null || !PenumbraC2S.allowC2S(player)
                || !holdsCamera(player)
                || !MUTATION_LIMITER.allow(player.getUUID(), player.level().getGameTime())) {
            return;
        }
        PhotoAlbumState album = PhotoAlbumState.get(player.level().getServer());
        UUID owner = player.getUUID();
        boolean wasPublic = album.find(payload.photoId())
                .map(PhotoAlbumState.StoredPhoto::publicVisible).orElse(false);
        boolean success = switch (payload.action()) {
            case MUTATION_RENAME -> album.rename(owner, payload.photoId(), payload.value());
            case MUTATION_DELETE -> album.delete(owner, payload.photoId());
            case MUTATION_VISIBILITY -> (payload.value().equals("true")
                    || payload.value().equals("false"))
                    && album.setPublic(owner, payload.photoId(),
                    Boolean.parseBoolean(payload.value()));
            case MUTATION_IMPORTANT -> (payload.value().equals("true")
                    || payload.value().equals("false"))
                    && album.setImportant(owner, payload.photoId(),
                    Boolean.parseBoolean(payload.value()));
            default -> false;
        };
        ServerPlayNetworking.send(player,
                new AlbumMutationResultS2C(payload.photoId(), payload.action(), success));
        if (success && payload.action() == MUTATION_DELETE) {
            removeInventoryReferences(player, payload.photoId());
            for (ServerPlayer listener : player.level().getServer().getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(listener, new PhotoRemovedS2C(payload.photoId()));
            }
        } else if (success && payload.action() == MUTATION_VISIBILITY
                && wasPublic && "false".equals(payload.value())) {
            PhotoAlbumState.StoredPhoto privatePhoto = album.find(payload.photoId()).orElse(null);
            if (privatePhoto != null) {
                // Révoque immédiatement le cache des anciens lecteurs publics.
                // Une personne qui possède encore un tirage physique conserve
                // volontairement son accès, comme avant l'existence de l'album UI.
                for (ServerPlayer listener : player.level().getServer()
                        .getPlayerList().getPlayers()) {
                    if (!mayView(listener, privatePhoto)) {
                        ServerPlayNetworking.send(listener,
                                new PhotoRemovedS2C(payload.photoId()));
                    }
                }
            }
        }
    }

    private static void removeInventoryReferences(ServerPlayer owner, UUID photoId) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            PhotoData data = stack.get(PhotoContent.PHOTO_DATA);
            if (data != null && data.photoId().equals(photoId)) {
                owner.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        owner.getInventory().setChanged();
    }

    /**
     * Valide côté serveur le négatif, son développement, l'accès du joueur et
     * le cooldown avant toute apparition. Le client ne peut donc ni choisir une
     * photo hantée ni forcer une série de manifestations.
     */
    private static void acceptObservation(ServerPlayer player, UUID photoId) {
        if (photoId == null || !PenumbraC2S.allowC2S(player)
                || !player.isAlive() || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        PhotoAlbumState.StoredPhoto stored = PhotoAlbumState.get(player.level().getServer())
                .find(photoId).orElse(null);
        if (stored == null || !stored.projectionist()
                || now - stored.capturedAtTick() < PhotoData.DEVELOPMENT_TICKS
                || !holdsPhoto(player, stored.id())) {
            return;
        }
        Long last = LAST_PROJECTIONIST_MANIFESTATIONS.get(player.getUUID());
        if (last != null && now >= last && now - last < PROJECTIONIST_COOLDOWN_TICKS) {
            return;
        }
        ServerLevel level = player.level();
        ProjectionistEntity manifestation = ProjectionistEntity.spawnBehind(level, player);
        if (manifestation == null) {
            return;
        }
        LAST_PROJECTIONIST_MANIFESTATIONS.put(player.getUUID(), now);
        player.addEffect(new MobEffectInstance(
                MobEffects.DARKNESS, 65, 0, false, false, false));
        ServerPlayNetworking.send(player, new PhotoManifestS2C(photoId));
    }

    private static boolean holdsPhoto(ServerPlayer player, UUID photoId) {
        PhotoData held = player.getMainHandItem().get(PhotoContent.PHOTO_DATA);
        return held != null && held.photoId().equals(photoId);
    }

    private static boolean mayView(ServerPlayer player, PhotoAlbumState.StoredPhoto stored) {
        if (stored.owner().equals(player.getUUID()) || stored.publicVisible()) {
            return true;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            PhotoData data = player.getInventory().getItem(slot).get(PhotoContent.PHOTO_DATA);
            if (data != null && data.photoId().equals(stored.id())) {
                return true;
            }
        }
        return !player.level().getEntitiesOfClass(
                PhotoFrameEntity.class,
                player.getBoundingBox().inflate(FRAME_VIEW_RADIUS),
                frame -> {
                    PhotoData data = frame.getItem().get(PhotoContent.PHOTO_DATA);
                    return data != null && data.photoId().equals(stored.id());
                }).isEmpty();
    }

    private static void broadcastFlash(ServerPlayer source) {
        ServerLevel level = source.level();
        double x = source.getX();
        double y = source.getEyeY();
        double z = source.getZ();
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0f, 0.98f, 0.90f),
                x, y, z, 1, 0.0, 0.0, 0.0, 0.0);
        double radiusSqr = FLASH_RADIUS * FLASH_RADIUS;
        for (ServerPlayer listener : level.players()) {
            if (listener != source && listener.distanceToSqr(source) <= radiusSqr) {
                ServerPlayNetworking.send(listener, new PhotoFlashS2C(x, y, z));
            }
        }
        PenumbraNetworking.sendSoundPriorityNearbyExcept(
                source, source, PenumbraSounds.BINOCULARS_ON, source.position(), 0.8f, 1.22f);
    }

    public static void clear(UUID playerId) {
        LAST_CAPTURES.remove(playerId);
        PENDING_CAPTURES.remove(playerId);
        // Le cooldown de manifestation survit à la reconnexion pendant la session,
        // sinon un joueur pourrait le contourner en quittant/rejoignant.
        REQUEST_LIMITER.clear(playerId);
        ANALYSIS_REQUEST_LIMITER.clear(playerId);
        ALBUM_REQUEST_LIMITER.clear(playerId);
        MUTATION_LIMITER.clear(playerId);
    }

    public static void clearAll() {
        LAST_CAPTURES.clear();
        PENDING_CAPTURES.clear();
        LAST_PROJECTIONIST_MANIFESTATIONS.clear();
        REQUEST_LIMITER.clear();
        ANALYSIS_REQUEST_LIMITER.clear();
        ALBUM_REQUEST_LIMITER.clear();
        MUTATION_LIMITER.clear();
    }

    /** Quatre réponses photo par seconde, sans burst lors de l'ouverture de la galerie. */
    private static boolean allowPhotoRequest(ServerPlayer player) {
        return REQUEST_LIMITER.allow(player.getUUID(), player.level().getGameTime());
    }

    private static boolean holdsCamera(ServerPlayer player) {
        return player.getMainHandItem().getItem() == PenumbraItems.BINOCULARS
                || player.getOffhandItem().getItem() == PenumbraItems.BINOCULARS;
    }

    private static List<String> evidenceIds(PhotoAlbumState.StoredPhoto stored) {
        return stored.evidence().stream().map(PhotoEvidence::id).toList();
    }

    private static List<String> normalizeEvidenceIds(List<String> requested,
                                                      boolean projectionist) {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (requested != null) {
            for (String id : requested) {
                PhotoEvidence evidence = PhotoEvidence.byId(id);
                if (evidence.discoverable()) {
                    normalized.add(evidence.id());
                    if (normalized.size() >= PhotoEvidence.MAX_PER_PHOTO) {
                        break;
                    }
                }
            }
        }
        if (projectionist && normalized.size() < PhotoEvidence.MAX_PER_PHOTO) {
            normalized.add(PhotoEvidence.PROJECTIONIST.id());
        }
        return List.copyOf(normalized);
    }

    private static void notifyDossierDiscovery(ServerPlayer player,
                                                PhotoAlbumState.EvidenceDiscovery discovery) {
        if (!discovery.changed()) {
            return;
        }
        if (discovery.newlyDiscovered().size() == 1) {
            PhotoEvidence evidence = discovery.newlyDiscovered().getFirst();
            player.sendSystemMessage(Component.translatable(
                    "message.penumbra.dossier.new_evidence",
                    Component.translatable(evidence.translationKey()),
                    discovery.discoveredCount(), PhotoEvidence.discoverableCount()));
        } else {
            player.sendSystemMessage(Component.translatable(
                    "message.penumbra.dossier.new_evidence_many",
                    discovery.newlyDiscovered().size(), discovery.discoveredCount(),
                    PhotoEvidence.discoverableCount()));
        }
        for (int stage = discovery.previousStage() + 1; stage <= discovery.newStage(); stage++) {
            player.sendSystemMessage(Component.translatable(
                    "message.penumbra.dossier.stage." + stage));
        }
        List<PhotoEvidence> after = PhotoAlbumState.get(player.level().getServer())
                .dossier(player.getUUID()).discovered();
        List<PhotoEvidence> before = new ArrayList<>(after);
        before.removeAll(discovery.newlyDiscovered());
        for (InvestigationClue clue : InvestigationRules.newlyUnlocked(before, after)) {
            player.sendSystemMessage(Component.translatable(
                    "message.penumbra.dossier.clue_unlocked",
                    Component.translatable(clue.translationKey()),
                    Component.translatable(clue.counterplay().translationKey())));
        }
    }

    private record PendingCapture(long sequence, long capturedAtTick,
                                  boolean projectionist, List<PhotoEvidence> evidence) {
        private PendingCapture {
            evidence = List.copyOf(evidence);
        }
    }

}
