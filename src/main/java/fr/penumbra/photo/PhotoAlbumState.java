package fr.penumbra.photo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Album binaire persistant du monde. Les piles ne transportent que leur UUID. */
public final class PhotoAlbumState extends SavedData {

    public static final int WIDTH = 128;
    public static final int HEIGHT = 72;
    public static final int RGB_BYTES = WIDTH * HEIGHT * 3;
    /** Capacité partagée du serveur : seize quotas individuels complets. */
    public static final int MAX_WORLD_PHOTOS = 2048;
    public static final int MAX_PLAYER_PHOTOS = 128;
    public static final int MAX_DOSSIERS = 4096;
    public static final int MAX_TITLE_LENGTH = 32;
    public static final int MAX_PAGE_SIZE = 12;

    public enum AlbumScope {
        OWNED,
        PUBLIC
    }

    /** Métadonnées légères : les pixels restent chargés séparément. */
    public record PhotoMetadata(UUID id, UUID owner, long capturedAtTick,
                                boolean projectionist, String title,
                                boolean publicVisible, List<PhotoEvidence> evidence,
                                boolean important) {
        public PhotoMetadata {
            evidence = normalizeEvidence(evidence);
        }
    }

    public record AlbumPage(List<PhotoMetadata> entries, int page, int pageSize,
                            int totalEntries) {
        public AlbumPage {
            entries = List.copyOf(entries);
        }

        public int totalPages() {
            return Math.max(1, (totalEntries + pageSize - 1) / pageSize);
        }
    }

    /** Ensemble dédupliqué de catégories déjà révélées par un joueur. */
    public record DossierProgress(List<PhotoEvidence> discovered) {
        private static final Codec<DossierProgress> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        PhotoEvidence.CODEC.listOf().optionalFieldOf("evidence", List.of())
                                .forGetter(DossierProgress::discovered)
                ).apply(instance, DossierProgress::new));

        public DossierProgress {
            discovered = PhotoEvidence.normalizeDossier(discovered);
        }

        public int count() {
            return discovered.size();
        }

        public int stage() {
            return PhotoEvidence.dossierStage(count());
        }

        public boolean contains(PhotoEvidence evidence) {
            return discovered.contains(evidence);
        }
    }

    /** Résultat idempotent d'une révélation serveur après développement. */
    public record EvidenceDiscovery(List<PhotoEvidence> newlyDiscovered,
                                    int previousStage, int newStage,
                                    int discoveredCount) {
        public EvidenceDiscovery {
            newlyDiscovered = List.copyOf(newlyDiscovered);
        }

        public boolean changed() {
            return !newlyDiscovered.isEmpty();
        }

        public boolean stageAdvanced() {
            return newStage > previousStage;
        }
    }

    private static final Codec<byte[]> PIXELS_CODEC = Codec.BYTE_BUFFER.comapFlatMap(buffer -> {
        ByteBuffer copy = buffer.duplicate();
        if (copy.remaining() != RGB_BYTES) {
            return DataResult.error(() -> "Invalid Penumbra photo length: " + copy.remaining());
        }
        byte[] pixels = new byte[RGB_BYTES];
        copy.get(pixels);
        return DataResult.success(pixels);
    }, pixels -> ByteBuffer.wrap(pixels.clone()));

    public record StoredPhoto(UUID id, UUID owner, long capturedAtTick,
                              byte[] pixels, boolean projectionist,
                              String title, boolean publicVisible,
                              List<PhotoEvidence> evidence, boolean important) {
        private static final Codec<StoredPhoto> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        UUIDUtil.CODEC.fieldOf("id").forGetter(StoredPhoto::id),
                        UUIDUtil.CODEC.fieldOf("owner").forGetter(StoredPhoto::owner),
                        Codec.LONG.fieldOf("captured_at").forGetter(StoredPhoto::capturedAtTick),
                        PIXELS_CODEC.fieldOf("pixels").forGetter(StoredPhoto::pixels),
                        Codec.BOOL.optionalFieldOf("projectionist", false)
                                .forGetter(StoredPhoto::projectionist),
                        Codec.STRING.optionalFieldOf("title", "").forGetter(StoredPhoto::title),
                        Codec.BOOL.optionalFieldOf("public", false)
                                .forGetter(StoredPhoto::publicVisible),
                        PhotoEvidence.CODEC.listOf().optionalFieldOf("evidence", List.of())
                                .forGetter(StoredPhoto::evidence),
                        Codec.BOOL.optionalFieldOf("important", false)
                                .forGetter(StoredPhoto::important)
                ).apply(instance, StoredPhoto::new));

        public StoredPhoto {
            if (id == null || owner == null || pixels == null || pixels.length != RGB_BYTES) {
                throw new IllegalArgumentException("Invalid stored Penumbra photo");
            }
            pixels = pixels.clone();
            title = sanitizeTitle(title);
            evidence = normalizeEvidence(evidence);
            // Migration 0.8/0.9 : les négatifs hantés n'avaient pas encore de catégorie.
            if (projectionist && evidence.isEmpty()) {
                evidence = List.of(PhotoEvidence.PROJECTIONIST);
            }
        }

        /** Compatibilité source et sauvegarde avec l'album antérieur à 0.7.0. */
        public StoredPhoto(UUID id, UUID owner, long capturedAtTick, byte[] pixels) {
            this(id, owner, capturedAtTick, pixels, false, "", false, List.of(), false);
        }

        /** Compatibilité source avec les négatifs du Projectionniste de 0.8.x. */
        public StoredPhoto(UUID id, UUID owner, long capturedAtTick, byte[] pixels,
                           boolean projectionist) {
            this(id, owner, capturedAtTick, pixels, projectionist, "", false,
                    projectionist ? List.of(PhotoEvidence.PROJECTIONIST) : List.of(), false);
        }

        /** Compatibilité source avec le record complet de 0.9.0. */
        public StoredPhoto(UUID id, UUID owner, long capturedAtTick, byte[] pixels,
                           boolean projectionist, String title, boolean publicVisible) {
            this(id, owner, capturedAtTick, pixels, projectionist, title, publicVisible,
                    projectionist ? List.of(PhotoEvidence.PROJECTIONIST) : List.of(), false);
        }

        @Override
        public byte[] pixels() {
            return pixels.clone();
        }
    }

    private static final Codec<Map<UUID, DossierProgress>> DOSSIERS_CODEC =
            Codec.unboundedMap(
                    Codec.STRING.xmap(UUID::fromString, UUID::toString),
                    DossierProgress.CODEC);

    public static final Codec<PhotoAlbumState> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    StoredPhoto.CODEC.listOf().optionalFieldOf("photos", List.of())
                            .forGetter(PhotoAlbumState::serializablePhotos),
                    DOSSIERS_CODEC.optionalFieldOf("dossiers", Map.of())
                            .forGetter(PhotoAlbumState::serializableDossiers)
            ).apply(instance, PhotoAlbumState::new));

    private static final SavedDataType<PhotoAlbumState> TYPE = new SavedDataType<>(
            PenumbraMod.id("photo_album"), PhotoAlbumState::new, CODEC, null);

    private final Map<UUID, StoredPhoto> photos = new LinkedHashMap<>();
    private final Map<UUID, DossierProgress> dossiers = new LinkedHashMap<>();

    public PhotoAlbumState() {
    }

    PhotoAlbumState(List<StoredPhoto> loaded) {
        this(loaded, Map.of());
    }

    PhotoAlbumState(List<StoredPhoto> loaded, Map<UUID, DossierProgress> loadedDossiers) {
        Map<UUID, Integer> loadedByOwner = new LinkedHashMap<>();
        for (StoredPhoto photo : loaded) {
            if (photos.size() >= MAX_WORLD_PHOTOS) {
                break;
            }
            if (photos.containsKey(photo.id())
                    || loadedByOwner.getOrDefault(photo.owner(), 0) >= MAX_PLAYER_PHOTOS) {
                continue;
            }
            photos.put(photo.id(), photo);
            loadedByOwner.merge(photo.owner(), 1, Integer::sum);
        }
        for (Map.Entry<UUID, DossierProgress> entry : loadedDossiers.entrySet()) {
            if (dossiers.size() >= MAX_DOSSIERS) {
                break;
            }
            if (entry.getKey() != null && entry.getValue() != null
                    && entry.getValue().count() > 0) {
                dossiers.putIfAbsent(entry.getKey(), new DossierProgress(entry.getValue().discovered()));
            }
        }
    }

    public static PhotoAlbumState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public Optional<StoredPhoto> add(UUID owner, long capturedAtTick, byte[] pixels) {
        return add(owner, capturedAtTick, pixels, false, List.of());
    }

    public Optional<StoredPhoto> add(UUID owner, long capturedAtTick, byte[] pixels,
                                     boolean projectionist) {
        return add(owner, capturedAtTick, pixels, projectionist,
                projectionist ? List.of(PhotoEvidence.PROJECTIONIST) : List.of());
    }

    /** Ajoute une capture et grave définitivement toutes les preuves tirées côté serveur. */
    public Optional<StoredPhoto> add(UUID owner, long capturedAtTick, byte[] pixels,
                                     boolean projectionist, List<PhotoEvidence> detectedEvidence) {
        if (owner == null || pixels == null || pixels.length != RGB_BYTES
                || photos.size() >= MAX_WORLD_PHOTOS) {
            return Optional.empty();
        }
        long owned = photos.values().stream().filter(photo -> photo.owner().equals(owner)).count();
        if (owned >= MAX_PLAYER_PHOTOS) {
            return Optional.empty();
        }
        List<PhotoEvidence> evidence = new ArrayList<>(normalizeEvidence(detectedEvidence));
        if (projectionist && !evidence.contains(PhotoEvidence.PROJECTIONIST)
                && evidence.size() < PhotoEvidence.MAX_PER_PHOTO) {
            evidence.add(PhotoEvidence.PROJECTIONIST);
        }
        UUID id;
        do {
            id = UUID.randomUUID();
        } while (photos.containsKey(id));
        StoredPhoto photo = new StoredPhoto(id, owner, capturedAtTick, pixels,
                projectionist, "", false, evidence, false);
        photos.put(id, photo);
        setDirty();
        return Optional.of(photo);
    }

    public Optional<StoredPhoto> find(UUID id) {
        return Optional.ofNullable(photos.get(id));
    }

    /** Liste bornée ; les favoris passent en tête uniquement dans l'album personnel. */
    public AlbumPage page(UUID viewer, AlbumScope scope, int requestedPage, int requestedPageSize) {
        if (viewer == null || scope == null) {
            return new AlbumPage(List.of(), 0, 1, 0);
        }
        int pageSize = Math.max(1, Math.min(MAX_PAGE_SIZE, requestedPageSize));
        Comparator<StoredPhoto> recent = Comparator
                .comparingLong(StoredPhoto::capturedAtTick).reversed()
                .thenComparing(StoredPhoto::id);
        Comparator<StoredPhoto> order = scope == AlbumScope.OWNED
                ? Comparator.comparing(StoredPhoto::important).reversed().thenComparing(recent)
                : recent;
        List<StoredPhoto> visible = photos.values().stream()
                .filter(photo -> scope == AlbumScope.OWNED
                        ? photo.owner().equals(viewer) : photo.publicVisible())
                .sorted(order)
                .toList();
        int total = visible.size();
        int maxPage = Math.max(0, (total - 1) / pageSize);
        int page = Math.max(0, Math.min(maxPage, requestedPage));
        int from = Math.min(total, page * pageSize);
        int to = Math.min(total, from + pageSize);
        List<PhotoMetadata> entries = visible.subList(from, to).stream()
                .map(PhotoAlbumState::metadata)
                .toList();
        return new AlbumPage(entries, page, pageSize, total);
    }

    public boolean rename(UUID owner, UUID photoId, String requestedTitle) {
        StoredPhoto current = ownedPhoto(owner, photoId);
        if (current == null) {
            return false;
        }
        String title = sanitizeTitle(requestedTitle);
        if (title.equals(current.title())) {
            return true;
        }
        photos.put(photoId, copyWith(current, title, current.publicVisible(), current.important()));
        setDirty();
        return true;
    }

    public boolean setPublic(UUID owner, UUID photoId, boolean publicVisible) {
        StoredPhoto current = ownedPhoto(owner, photoId);
        if (current == null) {
            return false;
        }
        if (publicVisible == current.publicVisible()) {
            return true;
        }
        photos.put(photoId, copyWith(current, current.title(), publicVisible, current.important()));
        setDirty();
        return true;
    }

    public boolean setImportant(UUID owner, UUID photoId, boolean important) {
        StoredPhoto current = ownedPhoto(owner, photoId);
        if (current == null) {
            return false;
        }
        if (important == current.important()) {
            return true;
        }
        photos.put(photoId, copyWith(current, current.title(), current.publicVisible(), important));
        setDirty();
        return true;
    }

    /** Suppression propriétaire uniquement ; le quota est libéré, le dossier reste acquis. */
    public boolean delete(UUID owner, UUID photoId) {
        StoredPhoto current = ownedPhoto(owner, photoId);
        if (current == null) {
            return false;
        }
        photos.remove(photoId);
        setDirty();
        return true;
    }

    public DossierProgress dossier(UUID owner) {
        DossierProgress progress = owner == null ? null : dossiers.get(owner);
        return progress == null ? new DossierProgress(List.of())
                : new DossierProgress(progress.discovered());
    }

    public EvidenceDiscovery recordEvidence(UUID owner, PhotoEvidence evidence) {
        return recordEvidence(owner, evidence == null ? List.of() : List.of(evidence));
    }

    public EvidenceDiscovery recordEvidence(UUID owner, List<PhotoEvidence> evidence) {
        DossierProgress before = dossier(owner);
        if (owner == null || evidence == null
                || !dossiers.containsKey(owner) && dossiers.size() >= MAX_DOSSIERS) {
            return unchanged(before);
        }
        LinkedHashSet<PhotoEvidence> merged = new LinkedHashSet<>(before.discovered());
        List<PhotoEvidence> added = new ArrayList<>();
        for (PhotoEvidence value : PhotoEvidence.normalizeDossier(evidence)) {
            if (merged.add(value)) {
                added.add(value);
            }
        }
        if (added.isEmpty()) {
            return unchanged(before);
        }
        DossierProgress after = new DossierProgress(new ArrayList<>(merged));
        dossiers.put(owner, after);
        setDirty();
        return new EvidenceDiscovery(added, before.stage(), after.stage(), after.count());
    }

    /** Réconciliation bornée appelée à l'ouverture de l'album du propriétaire. */
    public EvidenceDiscovery revealDeveloped(UUID owner, long now, int developmentTicks) {
        List<PhotoEvidence> revealed = photos.values().stream()
                .filter(photo -> photo.owner().equals(owner))
                .filter(photo -> PhotoEvidenceRules.isDeveloped(
                        now, photo.capturedAtTick(), developmentTicks))
                .flatMap(photo -> photo.evidence().stream())
                .toList();
        return recordEvidence(owner, revealed);
    }

    public int ownedCount(UUID owner) {
        if (owner == null) {
            return 0;
        }
        return (int) photos.values().stream()
                .filter(photo -> photo.owner().equals(owner)).count();
    }

    public static String sanitizeTitle(String requestedTitle) {
        if (requestedTitle == null || requestedTitle.isBlank()) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(MAX_TITLE_LENGTH);
        var codePoints = requestedTitle.codePoints().iterator();
        while (codePoints.hasNext()) {
            int codePoint = codePoints.nextInt();
            if (Character.isISOControl(codePoint) || codePoint == '§') {
                continue;
            }
            if (cleaned.length() + Character.charCount(codePoint) > MAX_TITLE_LENGTH) {
                break;
            }
            cleaned.appendCodePoint(codePoint);
        }
        String value = cleaned.toString().strip();
        return value.length() > MAX_TITLE_LENGTH ? value.substring(0, MAX_TITLE_LENGTH) : value;
    }

    public int size() {
        return photos.size();
    }

    int dossierCount() {
        return dossiers.size();
    }

    private StoredPhoto ownedPhoto(UUID owner, UUID photoId) {
        StoredPhoto current = photos.get(photoId);
        return current != null && owner != null && current.owner().equals(owner) ? current : null;
    }

    private List<StoredPhoto> serializablePhotos() {
        return new ArrayList<>(photos.values());
    }

    private Map<UUID, DossierProgress> serializableDossiers() {
        return new LinkedHashMap<>(dossiers);
    }

    private static PhotoMetadata metadata(StoredPhoto photo) {
        return new PhotoMetadata(photo.id(), photo.owner(), photo.capturedAtTick(),
                photo.projectionist(), photo.title(), photo.publicVisible(),
                photo.evidence(), photo.important());
    }

    private static StoredPhoto copyWith(StoredPhoto source, String title,
                                        boolean publicVisible, boolean important) {
        return new StoredPhoto(source.id(), source.owner(), source.capturedAtTick(),
                source.pixels(), source.projectionist(), title, publicVisible,
                source.evidence(), important);
    }

    private static EvidenceDiscovery unchanged(DossierProgress progress) {
        return new EvidenceDiscovery(List.of(), progress.stage(), progress.stage(), progress.count());
    }

    private static List<PhotoEvidence> normalizeEvidence(List<PhotoEvidence> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<PhotoEvidence> unique = new LinkedHashSet<>();
        for (PhotoEvidence value : evidence) {
            if (value != null && value.discoverable()) {
                unique.add(value);
                if (unique.size() >= PhotoEvidence.MAX_PER_PHOTO) {
                    break;
                }
            }
        }
        return List.copyOf(unique);
    }
}
