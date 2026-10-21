package fr.penumbra.client.photo;

import com.mojang.blaze3d.platform.NativeImage;
import fr.penumbra.PenumbraMod;
import fr.penumbra.config.PenumbraConfig;
import fr.penumbra.client.binoculars.BinocularsManager;
import fr.penumbra.network.PhotoNetworking;
import fr.penumbra.photo.PhotoAlbumState;
import fr.penumbra.photo.PhotoContent;
import fr.penumbra.photo.PhotoData;
import fr.penumbra.registry.PenumbraItems;
import fr.penumbra.registry.PenumbraSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionResult;

/** Entrées caméra/photo, capture framebuffer et flash multijoueur. */
public final class PhotoClientManager {

    private static final long LOCAL_CAPTURE_COOLDOWN_MS = 2_050L;
    private static final long SHUTTER_RESULT_TIMEOUT_MS = 4_000L;
    private static boolean capturing;
    private static long lastCaptureMillis = Long.MIN_VALUE;
    private static long captureSequence;
    private static long pendingSequence = -1L;
    private static long pendingSinceMillis;
    private static byte[] pendingPixels;
    private static boolean shutterAccepted;
    private static int flashTicks;
    private static float flashStrength;

    private PhotoClientManager() {
    }

    public static void register() {
        ClientPreAttackCallback.EVENT.register(PhotoClientManager::onPreAttack);
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide()
                    || player.getItemInHand(hand).getItem() != PenumbraItems.BINOCULARS) {
                return InteractionResult.PASS;
            }
            Minecraft client = Minecraft.getInstance();
            if (client.player == player && client.screen == null) {
                client.setScreen(new PhotoAlbumScreen());
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
        ClientTickEvents.END_CLIENT_TICK.register(PhotoClientManager::tick);
        // Fabric may deliver disconnect on Netty; photo textures belong to the render thread.
        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> client.execute(PhotoClientManager::reset));

        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.PhotoDataS2C.TYPE,
                (packet, context) -> context.client().execute(() -> PhotoClientStore.receive(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.PhotoShutterResultS2C.TYPE,
                (packet, context) -> context.client().execute(
                        () -> receiveShutterResult(context.client(), packet)));
        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.PhotoAnalysisS2C.TYPE,
                (packet, context) -> context.client().execute(
                        () -> PhotoClientStore.receiveAnalysis(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.PhotoFlashS2C.TYPE,
                (packet, context) -> context.client().execute(() -> triggerFlash(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.PhotoManifestS2C.TYPE,
                (packet, context) -> context.client().execute(
                        () -> triggerProjectionistManifestation(packet)));
        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.AlbumPageS2C.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof PhotoAlbumScreen album) {
                        album.receivePage(packet);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.AlbumMutationResultS2C.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    if (context.client().screen instanceof PhotoAlbumScreen album) {
                        album.receiveMutation(packet);
                    }
                }));
        ClientPlayNetworking.registerGlobalReceiver(PhotoNetworking.PhotoRemovedS2C.TYPE,
                (packet, context) -> context.client().execute(() -> {
                    PhotoClientStore.invalidate(packet.photoId());
                    if (context.client().screen instanceof PhotoAlbumScreen album) {
                        album.photoRemoved(packet.photoId());
                    } else if (context.client().screen instanceof PhotoViewerScreen viewer
                            && viewer.isViewing(packet.photoId())) {
                        viewer.photoRemoved(packet.photoId());
                    }
                }));

        HudElementRegistry.addLast(PenumbraMod.id("camera_flash"), (graphics, deltaTracker) -> {
            if (flashTicks <= 0 || flashStrength <= 0.0f || !PenumbraConfig.get().flashesEnabled) {
                return;
            }
            Minecraft client = Minecraft.getInstance();
            float life = flashTicks / 7.0f;
            int alpha = Math.max(0, Math.min(235, Math.round(235.0f * flashStrength * life * life)));
            graphics.fill(0, 0, client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight(), (alpha << 24) | 0xFFF6DC);
        });
    }

    private static boolean onPreAttack(Minecraft client, LocalPlayer player, int clickCount) {
        ItemStack held = player.getMainHandItem();
        PhotoData photo = held.get(PhotoContent.PHOTO_DATA);
        if (held.getItem() instanceof fr.penumbra.photo.CameraPhotoItem && photo != null) {
            if (clickCount > 0 && client.screen == null) {
                PhotoClientStore.ensureRequested(photo.photoId());
                client.setScreen(new PhotoViewerScreen(photo));
            }
            return true;
        }
        if (held.getItem() == PenumbraItems.BINOCULARS) {
            if (clickCount > 0 && client.screen == null) {
                capture(client);
            }
            return true;
        }
        return false;
    }

    private static void capture(Minecraft client) {
        long now = Util.getMillis();
        ClientPacketListener connection = client.getConnection();
        if (connection == null || capturing || pendingSequence >= 0L || lastCaptureMillis != Long.MIN_VALUE
                && now >= lastCaptureMillis && now - lastCaptureMillis < LOCAL_CAPTURE_COOLDOWN_MS
                || !ClientPlayNetworking.canSend(PhotoNetworking.PhotoShutterC2S.TYPE)
                || !ClientPlayNetworking.canSend(PhotoNetworking.PhotoCaptureC2S.TYPE)) {
            return;
        }
        capturing = true;
        lastCaptureMillis = now;
        long sequence = ++captureSequence;
        pendingSequence = sequence;
        pendingSinceMillis = now;
        pendingPixels = null;
        shutterAccepted = false;
        ClientPlayNetworking.send(new PhotoNetworking.PhotoShutterC2S(
                sequence, BinocularsManager.getZoomFactor()));
        triggerLocalCaptureFeedback(client);
        try {
            Screenshot.takeScreenshot(client.getMainRenderTarget(), screenshot -> {
                byte[] rgb = new byte[PhotoAlbumState.RGB_BYTES];
                try (screenshot;
                     NativeImage reduced = new NativeImage(PhotoAlbumState.WIDTH, PhotoAlbumState.HEIGHT, false)) {
                    int sourceWidth = screenshot.getWidth();
                    int sourceHeight = screenshot.getHeight();
                    // Isole l'intérieur du viseur 4:3, puis en tire un négatif
                    // 16:9 sans les larges bandes noires du boîtier de caméra.
                    int viewWidth = Math.min(Math.round(sourceWidth * 0.88f),
                            Math.round(sourceHeight * 0.86f * 4.0f / 3.0f));
                    int cropWidth = Math.max(1, Math.min(sourceWidth, viewWidth));
                    int cropHeight = Math.max(1,
                            cropWidth * PhotoAlbumState.HEIGHT / PhotoAlbumState.WIDTH);
                    cropHeight = Math.min(sourceHeight, cropHeight);
                    int cropX = Math.max(0, (sourceWidth - cropWidth) / 2);
                    int cropY = Math.max(0, (sourceHeight - cropHeight) / 2);
                    screenshot.resizeSubRectTo(cropX, cropY, cropWidth, cropHeight, reduced);
                    int cursor = 0;
                    for (int y = 0; y < PhotoAlbumState.HEIGHT; y++) {
                        for (int x = 0; x < PhotoAlbumState.WIDTH; x++) {
                            int pixel = reduced.getPixel(x, y);
                            rgb[cursor++] = (byte) ARGB.red(pixel);
                            rgb[cursor++] = (byte) ARGB.green(pixel);
                            rgb[cursor++] = (byte) ARGB.blue(pixel);
                        }
                    }
                } catch (RuntimeException error) {
                    failCapture(client, connection, sequence, error);
                    return;
                }
                client.execute(() -> {
                    if (!isCurrentCapture(client, connection, sequence)) {
                        return;
                    }
                    capturing = false;
                    pendingPixels = rgb;
                    trySendAcceptedCapture();
                });
            });
        } catch (RuntimeException error) {
            failCapture(client, connection, sequence, error);
        }
    }

    /** A late GPU readback must never complete or cancel another capture/session. */
    private static boolean isCurrentCapture(Minecraft client, ClientPacketListener connection,
                                             long sequence) {
        return client.getConnection() == connection && pendingSequence == sequence;
    }

    private static void receiveShutterResult(
            Minecraft client, PhotoNetworking.PhotoShutterResultS2C packet) {
        if (packet.sequence() != pendingSequence) {
            return;
        }
        if (!packet.accepted()) {
            clearPendingCapture();
            capturing = false;
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable("message.penumbra.photo.recharging"));
            }
            return;
        }
        shutterAccepted = true;
        trySendAcceptedCapture();
    }

    private static void trySendAcceptedCapture() {
        if (!shutterAccepted || pendingSequence < 0L || pendingPixels == null
                || !ClientPlayNetworking.canSend(PhotoNetworking.PhotoCaptureC2S.TYPE)) {
            return;
        }
        long sequence = pendingSequence;
        byte[] pixels = pendingPixels;
        clearPendingCapture();
        ClientPlayNetworking.send(new PhotoNetworking.PhotoCaptureC2S(sequence, pixels));
    }

    private static void clearPendingCapture() {
        pendingSequence = -1L;
        pendingSinceMillis = 0L;
        pendingPixels = null;
        shutterAccepted = false;
    }

    /** Retour instantané local ; le serveur ne renvoie ensuite le flash qu'aux voisins. */
    private static void triggerLocalCaptureFeedback(Minecraft client) {
        flashStrength = 1.0f;
        flashTicks = 7;
        if (client.player != null && client.level != null) {
            client.level.playLocalSound(client.player.getX(), client.player.getY(), client.player.getZ(),
                    PenumbraSounds.BINOCULARS_ON, SoundSource.PLAYERS, 0.8f, 1.22f, false);
        }
    }

    private static void failCapture(Minecraft client, ClientPacketListener connection,
                                      long sequence, RuntimeException error) {
        client.execute(() -> {
            if (!isCurrentCapture(client, connection, sequence)) {
                return;
            }
            PenumbraMod.LOGGER.warn("[Penumbra] Échec de capture photo", error);
            capturing = false;
            clearPendingCapture();
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable("message.penumbra.photo.capture_failed"));
            }
        });
    }

    private static void triggerFlash(PhotoNetworking.PhotoFlashS2C packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !PenumbraConfig.get().flashesEnabled) {
            return;
        }
        double dx = client.player.getX() - packet.x();
        double dy = client.player.getEyeY() - packet.y();
        double dz = client.player.getZ() - packet.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float strength = (float) Math.max(0.12, 1.0 - distance / PhotoNetworking.FLASH_RADIUS);
        flashStrength = Math.max(flashStrength, strength);
        flashTicks = Math.max(flashTicks, 7);
    }

    /**
     * Le serveur n'envoie ce paquet qu'après avoir réellement placé l'entité :
     * la photo se rabat alors brutalement et rend la vue au joueur.
     */
    private static void triggerProjectionistManifestation(
            PhotoNetworking.PhotoManifestS2C packet) {
        Minecraft client = Minecraft.getInstance();
        if (client.screen instanceof PhotoViewerScreen viewer
                && viewer.isViewing(packet.photoId())) {
            viewer.onClose();
        }
        flashStrength = Math.max(flashStrength, 0.38f);
        flashTicks = Math.max(flashTicks, 6);
    }

    private static void tick(Minecraft client) {
        PhotoClientStore.tick(client);
        long now = Util.getMillis();
        if (pendingSequence >= 0L && now >= pendingSinceMillis
                && now - pendingSinceMillis > SHUTTER_RESULT_TIMEOUT_MS) {
            clearPendingCapture();
            capturing = false;
            if (client.player != null) {
                client.player.sendOverlayMessage(
                        Component.translatable("message.penumbra.photo.capture_failed"));
            }
        }
        if (flashTicks > 0) {
            flashTicks--;
            if (flashTicks == 0) {
                flashStrength = 0.0f;
            }
        }
    }

    private static void reset() {
        capturing = false;
        lastCaptureMillis = Long.MIN_VALUE;
        // Keep tickets unique across reconnects, including queued shutter replies.
        clearPendingCapture();
        flashTicks = 0;
        flashStrength = 0.0f;
        PhotoClientStore.clear();
    }
}
