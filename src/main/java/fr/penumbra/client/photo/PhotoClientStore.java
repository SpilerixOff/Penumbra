package fr.penumbra.client.photo;

import com.mojang.blaze3d.platform.NativeImage;
import fr.penumbra.PenumbraMod;
import fr.penumbra.network.PhotoNetworking;
import fr.penumbra.photo.PhotoAlbumState;
import fr.penumbra.photo.PhotoData;
import fr.penumbra.photo.PhotoEvidence;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Cache LRU de textures dynamiques, dimensionné au quota complet d'un joueur. */
public final class PhotoClientStore {

    private static final int MAX_CACHE = PhotoAlbumState.MAX_PLAYER_PHOTOS;
    private static final int DEVELOPMENT_STEPS = 20;
    private static final Map<UUID, Entry> CACHE = new LinkedHashMap<>(16, 0.75f, true);
    /** Dernier envoi de demande ; une absence/refus est retenté après 3 s. */
    private static final Map<UUID, Long> PENDING = new LinkedHashMap<>();
    private static final Set<UUID> QUEUED = new LinkedHashSet<>();
    private static final Map<UUID, Long> ANALYSIS_PENDING = new LinkedHashMap<>();
    private static final Set<UUID> ANALYSIS_QUEUED = new LinkedHashSet<>();
    private static final long REQUEST_RETRY_MS = 3_000L;
    private static final long REQUEST_INTERVAL_MS = 260L;
    private static long lastRequestMillis = Long.MIN_VALUE;
    private static final Identifier PROJECTIONIST_OVERLAY =
            PenumbraMod.id("textures/entity/projectionist_photo.png");
    private static final Identifier ABSENT_TRUE_FORM_OVERLAY =
            PenumbraMod.id("textures/entity/absent_photo_truth.png");
    private static int[] projectionistOverlay;
    private static boolean projectionistOverlayLoaded;
    private static int[] absentTrueFormOverlay;
    private static boolean absentTrueFormOverlayLoaded;

    private PhotoClientStore() {
    }

    public static void receive(PhotoNetworking.PhotoDataS2C packet) {
        byte[] pixels = packet.pixels();
        if (pixels.length != PhotoAlbumState.RGB_BYTES) {
            PenumbraMod.LOGGER.warn("[Penumbra] Photo S2C rejetée : {} octets", pixels.length);
            return;
        }
        Minecraft client = Minecraft.getInstance();
        PENDING.remove(packet.photoId());
        QUEUED.remove(packet.photoId());
        Entry previous = CACHE.remove(packet.photoId());
        if (previous != null) {
            client.getTextureManager().release(previous.textureId);
        }

        NativeImage image = new NativeImage(PhotoAlbumState.WIDTH, PhotoAlbumState.HEIGHT, false);
        DynamicTexture texture = new DynamicTexture(
                () -> "Penumbra photo " + packet.photoId(), image);
        Identifier textureId = PenumbraMod.id("dynamic_photo/"
                + packet.photoId().toString().replace("-", ""));
        client.getTextureManager().register(textureId, texture);
        List<PhotoEvidence> evidence = packet.evidenceIds().stream()
                .map(PhotoEvidence::byId)
                .filter(PhotoEvidence::discoverable)
                .distinct()
                .limit(PhotoEvidence.MAX_PER_PHOTO)
                .toList();
        Entry entry = new Entry(packet.capturedAtTick(), pixels, packet.projectionist(),
                packet.absentTrueForm(),
                evidence, packet.evidenceRevealed(), textureId, texture, image);
        CACHE.put(packet.photoId(), entry);
        entry.update(client.level == null ? packet.capturedAtTick() : client.level.getGameTime());
        evict(client);
    }

    public static void ensureRequested(UUID photoId) {
        if (photoId == null || CACHE.containsKey(photoId)) {
            return;
        }
        long now = Util.getMillis();
        Long previous = PENDING.get(photoId);
        if (previous != null && now >= previous && now - previous < REQUEST_RETRY_MS) {
            return;
        }
        QUEUED.add(photoId);
    }

    /** Programme un verdict léger sans retélécharger les 27 Kio de pixels. */
    public static void ensureAnalysisRequested(UUID photoId) {
        Entry entry = CACHE.get(photoId);
        if (photoId == null || entry == null || entry.analysisRevealed) {
            return;
        }
        long now = Util.getMillis();
        Long previous = ANALYSIS_PENDING.get(photoId);
        if (previous != null && now >= previous && now - previous < REQUEST_RETRY_MS) {
            return;
        }
        ANALYSIS_QUEUED.add(photoId);
    }

    public static void receiveAnalysis(PhotoNetworking.PhotoAnalysisS2C packet) {
        ANALYSIS_PENDING.remove(packet.photoId());
        ANALYSIS_QUEUED.remove(packet.photoId());
        Entry entry = CACHE.get(packet.photoId());
        if (entry == null) {
            return;
        }
        entry.evidence = packet.evidenceIds().stream()
                .map(PhotoEvidence::byId)
                .filter(PhotoEvidence::discoverable)
                .distinct()
                .limit(PhotoEvidence.MAX_PER_PHOTO)
                .toList();
        entry.analysisRevealed = true;
        // Le verdict peut ajouter la vraie forme de l'Absent alors que la
        // texture avait déjà atteint son dernier palier de développement.
        // Invalider ce palier force la gravure de la double exposition.
        entry.renderedStep = -1;
    }

    public static Identifier texture(UUID photoId) {
        Entry entry = CACHE.get(photoId);
        return entry == null ? null : entry.textureId;
    }

    public static float progress(UUID photoId, long fallbackCapturedAt) {
        Entry entry = CACHE.get(photoId);
        long capturedAt = entry == null ? fallbackCapturedAt : entry.capturedAtTick;
        Minecraft client = Minecraft.getInstance();
        long gameTime = client.level == null ? capturedAt : client.level.getGameTime();
        return PhotoData.developmentProgress(gameTime, capturedAt);
    }

    /**
     * Distingue une analyse non terminée ({@code Optional.empty()}) d'une photo
     * développée sans anomalie ({@code Optional.of(List.of())}).
     */
    public static Optional<List<PhotoEvidence>> evidenceIfDeveloped(
            UUID photoId, long fallbackCapturedAt) {
        Entry entry = CACHE.get(photoId);
        if (entry == null || !entry.analysisRevealed) {
            return Optional.empty();
        }
        Minecraft client = Minecraft.getInstance();
        long gameTime = client.level == null ? fallbackCapturedAt : client.level.getGameTime();
        if (PhotoData.developmentProgress(gameTime, entry.capturedAtTick) < 1.0f) {
            return Optional.empty();
        }
        return Optional.of(entry.evidence);
    }

    public static void tick(Minecraft client) {
        drainQueue();
        if (client.level == null) {
            return;
        }
        long gameTime = client.level.getGameTime();
        for (Entry entry : CACHE.values()) {
            entry.update(gameTime);
        }
    }

    public static void clear() {
        Minecraft client = Minecraft.getInstance();
        for (Entry entry : CACHE.values()) {
            client.getTextureManager().release(entry.textureId);
        }
        CACHE.clear();
        PENDING.clear();
        QUEUED.clear();
        ANALYSIS_PENDING.clear();
        ANALYSIS_QUEUED.clear();
        lastRequestMillis = Long.MIN_VALUE;
        projectionistOverlay = null;
        projectionistOverlayLoaded = false;
        absentTrueFormOverlay = null;
        absentTrueFormOverlayLoaded = false;
    }

    public static void invalidate(UUID photoId) {
        if (photoId == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        QUEUED.remove(photoId);
        PENDING.remove(photoId);
        ANALYSIS_QUEUED.remove(photoId);
        ANALYSIS_PENDING.remove(photoId);
        Entry removed = CACHE.remove(photoId);
        if (removed != null) {
            client.getTextureManager().release(removed.textureId);
        }
    }

    public static boolean isLoaded(UUID photoId) {
        return photoId != null && CACHE.containsKey(photoId);
    }

    /** Une seule image à la fois : la galerie ne provoque aucun burst réseau. */
    private static void drainQueue() {
        if (QUEUED.isEmpty() && ANALYSIS_QUEUED.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        if (lastRequestMillis != Long.MIN_VALUE && now >= lastRequestMillis
                && now - lastRequestMillis < REQUEST_INTERVAL_MS) {
            return;
        }
        if (!QUEUED.isEmpty()
                && ClientPlayNetworking.canSend(PhotoNetworking.PhotoRequestC2S.TYPE)) {
            Iterator<UUID> iterator = QUEUED.iterator();
            UUID photoId = iterator.next();
            iterator.remove();
            if (CACHE.containsKey(photoId)) {
                return;
            }
            lastRequestMillis = now;
            PENDING.put(photoId, now);
            ClientPlayNetworking.send(new PhotoNetworking.PhotoRequestC2S(photoId));
            return;
        }
        if (!ANALYSIS_QUEUED.isEmpty()
                && ClientPlayNetworking.canSend(PhotoNetworking.PhotoAnalysisRequestC2S.TYPE)) {
            Iterator<UUID> iterator = ANALYSIS_QUEUED.iterator();
            UUID photoId = iterator.next();
            iterator.remove();
            Entry entry = CACHE.get(photoId);
            if (entry == null || entry.analysisRevealed) {
                return;
            }
            lastRequestMillis = now;
            ANALYSIS_PENDING.put(photoId, now);
            ClientPlayNetworking.send(new PhotoNetworking.PhotoAnalysisRequestC2S(photoId));
        }
    }

    private static void evict(Minecraft client) {
        Iterator<Map.Entry<UUID, Entry>> iterator = CACHE.entrySet().iterator();
        while (CACHE.size() > MAX_CACHE && iterator.hasNext()) {
            Entry entry = iterator.next().getValue();
            iterator.remove();
            client.getTextureManager().release(entry.textureId);
        }
    }

    private static final class Entry {
        private final long capturedAtTick;
        private final byte[] rgb;
        private final boolean projectionist;
        private final boolean absentTrueForm;
        private List<PhotoEvidence> evidence;
        private boolean analysisRevealed;
        private final Identifier textureId;
        private final DynamicTexture texture;
        private final NativeImage image;
        private int renderedStep = -1;

        private Entry(long capturedAtTick, byte[] rgb, boolean projectionist,
                      boolean absentTrueForm,
                      List<PhotoEvidence> evidence, boolean analysisRevealed, Identifier textureId,
                      DynamicTexture texture, NativeImage image) {
            this.capturedAtTick = capturedAtTick;
            this.rgb = rgb.clone();
            this.projectionist = projectionist;
            this.absentTrueForm = absentTrueForm;
            this.evidence = List.copyOf(evidence);
            this.analysisRevealed = analysisRevealed;
            this.textureId = textureId;
            this.texture = texture;
            this.image = image;
        }

        private void update(long gameTime) {
            float linear = PhotoData.developmentProgress(gameTime, capturedAtTick);
            int step = Math.min(DEVELOPMENT_STEPS, (int) Math.floor(linear * DEVELOPMENT_STEPS));
            if (step == renderedStep) {
                return;
            }
            renderedStep = step;
            float p = step / (float) DEVELOPMENT_STEPS;
            float eased = p * p * (3.0f - 2.0f * p);
            int[] apparitionPixels = projectionist && step > 0
                    ? projectionistPixels() : null;
            int[] absentPixels = absentTrueForm && step > 0
                    ? absentTrueFormPixels() : null;
            int cursor = 0;
            for (int y = 0; y < PhotoAlbumState.HEIGHT; y++) {
                for (int x = 0; x < PhotoAlbumState.WIDTH; x++) {
                    int sourceR = rgb[cursor++] & 0xFF;
                    int sourceG = rgb[cursor++] & 0xFF;
                    int sourceB = rgb[cursor++] & 0xFF;
                    int r;
                    int g;
                    int b;
                    if (step == 0) {
                        r = 2;
                        g = 3;
                        b = 4;
                    } else if (step == DEVELOPMENT_STEPS) {
                        r = sourceR;
                        g = sourceG;
                        b = sourceB;
                    } else {
                        int luminance = (sourceR * 54 + sourceG * 183 + sourceB * 19) >> 8;
                        float reveal = Math.max(0.0f, Math.min(1.0f, (eased - 0.02f) / 0.98f));
                        float colour = Math.max(0.0f, Math.min(1.0f, (eased - 0.38f) / 0.62f));
                        float brightness = 0.025f + 0.975f * reveal;
                        int sepiaR = clamp(Math.round(luminance * 1.08f * brightness));
                        int sepiaG = clamp(Math.round(luminance * 0.91f * brightness));
                        int sepiaB = clamp(Math.round(luminance * 0.66f * brightness));
                        int noise = grain(x, y, step) * Math.round((1.0f - eased) * 5.0f);
                        r = clamp(Math.round(sepiaR + (sourceR - sepiaR) * colour) + noise);
                        g = clamp(Math.round(sepiaG + (sourceG - sepiaG) * colour) + noise);
                        b = clamp(Math.round(sepiaB + (sourceB - sepiaB) * colour) + noise);
                    }
                    if (apparitionPixels != null) {
                        int apparition = apparitionPixels[y * PhotoAlbumState.WIDTH + x];
                        float apparitionAlpha = (ARGB.alpha(apparition) / 255.0f)
                                * projectionistReveal(eased);
                        if (apparitionAlpha > 0.0f) {
                            r = mix(r, ARGB.red(apparition), apparitionAlpha);
                            g = mix(g, ARGB.green(apparition), apparitionAlpha);
                            b = mix(b, ARGB.blue(apparition), apparitionAlpha);
                        }
                    }
                    if (absentPixels != null) {
                        int trueForm = absentPixels[y * PhotoAlbumState.WIDTH + x];
                        float trueFormAlpha = (ARGB.alpha(trueForm) / 255.0f)
                                * absentTrueFormReveal(eased);
                        if (trueFormAlpha > 0.0f) {
                            r = mix(r, ARGB.red(trueForm), trueFormAlpha);
                            g = mix(g, ARGB.green(trueForm), trueFormAlpha);
                            b = mix(b, ARGB.blue(trueForm), trueFormAlpha);
                        }
                    }
                    image.setPixel(x, y, ARGB.color(255, r, g, b));
                }
            }
            texture.upload();
        }

        private static int grain(int x, int y, int step) {
            int hash = x * 0x1F123BB5 ^ y * 0x5F356495 ^ step * 0x2C1B3C6D;
            hash ^= hash >>> 16;
            hash *= 0x7FEB352D;
            hash ^= hash >>> 15;
            return Math.floorMod(hash, 3) - 1;
        }

        private static int clamp(int value) {
            return Math.max(0, Math.min(255, value));
        }

        private static int mix(int from, int to, float amount) {
            return clamp(Math.round(from + (to - from) * amount));
        }

        /**
         * La silhouette commence à remonter du bain après ~15 % et reste gravée
         * dans le tirage final. Le smoothstep évite une apparition en un frame.
         */
        private static float projectionistReveal(float development) {
            float value = Math.max(0.0f, Math.min(1.0f,
                    (development - 0.14f) / 0.62f));
            return value * value * (3.0f - 2.0f * value);
        }

        /** La seconde silhouette n'apparaît qu'à la fin du bain. */
        private static float absentTrueFormReveal(float development) {
            float value = Math.max(0.0f, Math.min(1.0f,
                    (development - 0.58f) / 0.34f));
            return value * value * (3.0f - 2.0f * value);
        }
    }

    /**
     * Double exposition de la vraie forme. L'asset 128x72 est mis à l'échelle
     * défensivement ; un dessin procédural garantit la preuve même lorsqu'un
     * pack de ressources masque ou corrompt la texture.
     */
    private static int[] absentTrueFormPixels() {
        if (absentTrueFormOverlayLoaded) {
            return absentTrueFormOverlay;
        }
        absentTrueFormOverlayLoaded = true;
        absentTrueFormOverlay = fallbackAbsentTrueForm();
        Minecraft client = Minecraft.getInstance();
        var resource = client.getResourceManager().getResource(ABSENT_TRUE_FORM_OVERLAY);
        if (resource.isEmpty()) {
            PenumbraMod.LOGGER.warn("[Penumbra] Vraie forme photo de l'Absent absente");
            return absentTrueFormOverlay;
        }
        try (InputStream input = resource.get().open();
             NativeImage source = NativeImage.read(input)) {
            int[] scaled = new int[PhotoAlbumState.WIDTH * PhotoAlbumState.HEIGHT];
            for (int y = 0; y < PhotoAlbumState.HEIGHT; y++) {
                int sourceY = Math.min(source.getHeight() - 1,
                        y * source.getHeight() / PhotoAlbumState.HEIGHT);
                for (int x = 0; x < PhotoAlbumState.WIDTH; x++) {
                    int sourceX = Math.min(source.getWidth() - 1,
                            x * source.getWidth() / PhotoAlbumState.WIDTH);
                    scaled[y * PhotoAlbumState.WIDTH + x] =
                            source.getPixel(sourceX, sourceY);
                }
            }
            absentTrueFormOverlay = scaled;
        } catch (IOException | RuntimeException error) {
            PenumbraMod.LOGGER.warn(
                    "[Penumbra] Vraie forme photo de l'Absent illisible", error);
        }
        return absentTrueFormOverlay;
    }

    private static int[] fallbackAbsentTrueForm() {
        int[] pixels = new int[PhotoAlbumState.WIDTH * PhotoAlbumState.HEIGHT];
        int centerX = PhotoAlbumState.WIDTH / 2;
        int shadow = ARGB.color(190, 2, 5, 8);
        int fringe = ARGB.color(115, 18, 42, 51);

        // Crâne trop haut et torse étroit, décalés comme sur une pellicule
        // ayant enregistré deux instants incompatibles.
        fillEllipse(pixels, centerX, 18, 9, 13, shadow);
        fillRect(pixels, centerX - 7, 28, centerX + 7, 59, shadow);
        for (int y = 33; y < 67; y++) {
            int reach = 10 + (y - 33) / 2;
            put(pixels, centerX - reach, y, fringe);
            put(pixels, centerX - reach - 1, y, shadow);
            put(pixels, centerX + reach, y, fringe);
            put(pixels, centerX + reach + 1, y, shadow);
        }
        fillRect(pixels, centerX - 7, 59, centerX - 2, 71, shadow);
        fillRect(pixels, centerX + 2, 59, centerX + 7, 71, shadow);

        // Deux points froids et une fente verticale suffisent à contredire le
        // visage humain visible dans les pixels capturés.
        int eye = ARGB.color(235, 176, 226, 232);
        fillRect(pixels, centerX - 5, 17, centerX - 3, 18, eye);
        fillRect(pixels, centerX + 3, 17, centerX + 5, 18, eye);
        fillRect(pixels, centerX, 21, centerX, 26,
                ARGB.color(225, 0, 0, 0));
        return pixels;
    }

    private static void fillRect(int[] pixels, int minX, int minY,
                                 int maxX, int maxY, int color) {
        for (int y = Math.max(0, minY); y <= Math.min(PhotoAlbumState.HEIGHT - 1, maxY); y++) {
            for (int x = Math.max(0, minX); x <= Math.min(PhotoAlbumState.WIDTH - 1, maxX); x++) {
                put(pixels, x, y, color);
            }
        }
    }

    private static void fillEllipse(int[] pixels, int centerX, int centerY,
                                    int radiusX, int radiusY, int color) {
        for (int y = centerY - radiusY; y <= centerY + radiusY; y++) {
            for (int x = centerX - radiusX; x <= centerX + radiusX; x++) {
                double nx = (x - centerX) / (double) radiusX;
                double ny = (y - centerY) / (double) radiusY;
                if (nx * nx + ny * ny <= 1.0) {
                    put(pixels, x, y, color);
                }
            }
        }
    }

    private static void put(int[] pixels, int x, int y, int color) {
        if (x >= 0 && x < PhotoAlbumState.WIDTH
                && y >= 0 && y < PhotoAlbumState.HEIGHT) {
            pixels[y * PhotoAlbumState.WIDTH + x] = color;
        }
    }

    /**
     * Charge l'overlay RGBA 128×72 fourni par les ressources. Un dessin
     * procédural de secours garantit que les négatifs hantés restent lisibles
     * même si un pack de ressources masque ou corrompt l'asset.
     */
    private static int[] projectionistPixels() {
        if (projectionistOverlayLoaded) {
            return projectionistOverlay;
        }
        projectionistOverlayLoaded = true;
        projectionistOverlay = fallbackProjectionist();
        Minecraft client = Minecraft.getInstance();
        var resource = client.getResourceManager().getResource(PROJECTIONIST_OVERLAY);
        if (resource.isEmpty()) {
            PenumbraMod.LOGGER.warn("[Penumbra] Silhouette photo du Projectionniste absente");
            return projectionistOverlay;
        }
        try (InputStream input = resource.get().open();
             NativeImage source = NativeImage.read(input)) {
            int[] scaled = new int[PhotoAlbumState.WIDTH * PhotoAlbumState.HEIGHT];
            for (int y = 0; y < PhotoAlbumState.HEIGHT; y++) {
                int sourceY = Math.min(source.getHeight() - 1,
                        y * source.getHeight() / PhotoAlbumState.HEIGHT);
                for (int x = 0; x < PhotoAlbumState.WIDTH; x++) {
                    int sourceX = Math.min(source.getWidth() - 1,
                            x * source.getWidth() / PhotoAlbumState.WIDTH);
                    scaled[y * PhotoAlbumState.WIDTH + x] =
                            source.getPixel(sourceX, sourceY);
                }
            }
            projectionistOverlay = scaled;
        } catch (IOException | RuntimeException error) {
            PenumbraMod.LOGGER.warn(
                    "[Penumbra] Silhouette photo du Projectionniste illisible", error);
        }
        return projectionistOverlay;
    }

    private static int[] fallbackProjectionist() {
        int[] pixels = new int[PhotoAlbumState.WIDTH * PhotoAlbumState.HEIGHT];
        int centerX = 78;
        for (int y = 13; y < 69; y++) {
            float normalized = (y - 13) / 56.0f;
            int halfWidth;
            if (y < 27) {
                double dy = (y - 20.0) / 8.0;
                halfWidth = Math.max(1, (int) Math.round(7.0 * Math.sqrt(
                        Math.max(0.0, 1.0 - dy * dy))));
            } else {
                halfWidth = Math.round(7.0f + normalized * 8.0f);
            }
            for (int x = centerX - halfWidth; x <= centerX + halfWidth; x++) {
                if (x >= 0 && x < PhotoAlbumState.WIDTH) {
                    pixels[y * PhotoAlbumState.WIDTH + x] =
                            ARGB.color(225, 4, 6, 7);
                }
            }
        }
        // Lentille blanche, seul détail franchement surnaturel.
        for (int y = 18; y <= 21; y++) {
            for (int x = centerX - 2; x <= centerX + 2; x++) {
                pixels[y * PhotoAlbumState.WIDTH + x] =
                        ARGB.color(245, 210, 218, 201);
            }
        }
        return pixels;
    }
}
