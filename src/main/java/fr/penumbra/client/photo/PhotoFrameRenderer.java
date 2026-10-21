package fr.penumbra.client.photo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.penumbra.PenumbraMod;
import fr.penumbra.photo.PhotoContent;
import fr.penumbra.photo.PhotoData;
import fr.penumbra.photo.PhotoFrameEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/** Cadre bois/métal dessiné sur mesure et vraie capture rendue dans son ouverture. */
public final class PhotoFrameRenderer extends EntityRenderer<PhotoFrameEntity, PhotoFrameRenderState> {

    private static final Identifier FRAME_TEXTURE =
            PenumbraMod.id("textures/item/photo_frame.png");
    private static final Identifier FRAME_SIDE_TEXTURE =
            PenumbraMod.id("textures/block/photo_frame_side.png");

    public PhotoFrameRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public PhotoFrameRenderState createRenderState() {
        return new PhotoFrameRenderState();
    }

    @Override
    public void extractRenderState(PhotoFrameEntity entity, PhotoFrameRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.direction = entity.getDirection();
        PhotoData photo = entity.getItem().get(PhotoContent.PHOTO_DATA);
        if (photo == null) {
            state.photoId = null;
            state.capturedAtTick = 0L;
        } else {
            state.photoId = photo.photoId();
            state.capturedAtTick = photo.capturedAtTick();
            PhotoClientStore.ensureRequested(photo.photoId());
        }
    }

    @Override
    public Vec3 getRenderOffset(PhotoFrameRenderState state) {
        return new Vec3(state.direction.getStepX() * 0.3f, -0.25,
                state.direction.getStepZ() * 0.3f);
    }

    @Override
    public void submit(PhotoFrameRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState cameraState) {
        super.submit(state, poseStack, collector, cameraState);
        poseStack.pushPose();
        Vec3 offset = getRenderOffset(state);
        poseStack.translate(-offset.x, -offset.y, -offset.z);
        Direction direction = state.direction;
        poseStack.translate(direction.getStepX() * 0.46875,
                direction.getStepY() * 0.46875,
                direction.getStepZ() * 0.46875);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - direction.toYRot()));

        submitFrameDepth(collector, poseStack, state.lightCoords);
        submitQuad(collector, poseStack, FRAME_TEXTURE,
                -0.36f, -0.29f, 0.36f, 0.29f, 0.417f, state.lightCoords);
        if (state.photoId != null) {
            Identifier photoTexture = PhotoClientStore.texture(state.photoId);
            if (photoTexture != null) {
                submitQuad(collector, poseStack, photoTexture,
                        -0.23f, -0.13f, 0.23f, 0.13f, 0.413f, state.lightCoords);
            }
        }
        poseStack.popPose();
    }

    /** Ajoute un dos et de vraies tranches : le cadre reste lisible aux angles rasants. */
    private static void submitFrameDepth(SubmitNodeCollector collector, PoseStack poseStack, int light) {
        final float left = -0.36f;
        final float right = 0.36f;
        final float bottom = -0.29f;
        final float top = 0.29f;
        // Dans le repère du cadre vanilla, +Z va vers le bloc support : le dos
        // reste juste avant la face du mur (0.46875) et l'avant regarde vers -Z.
        final float front = 0.417f;
        final float back = 0.462f;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(FRAME_SIDE_TEXTURE),
                (pose, vertices) -> {
                    // Dos.
                    vertex(vertices, pose, left, bottom, back, 0.0f, 1.0f, light, 0, 0, 1);
                    vertex(vertices, pose, right, bottom, back, 1.0f, 1.0f, light, 0, 0, 1);
                    vertex(vertices, pose, right, top, back, 1.0f, 0.0f, light, 0, 0, 1);
                    vertex(vertices, pose, left, top, back, 0.0f, 0.0f, light, 0, 0, 1);
                    // Tranche gauche.
                    vertex(vertices, pose, left, bottom, front, 0.0f, 1.0f, light, -1, 0, 0);
                    vertex(vertices, pose, left, bottom, back, 1.0f, 1.0f, light, -1, 0, 0);
                    vertex(vertices, pose, left, top, back, 1.0f, 0.0f, light, -1, 0, 0);
                    vertex(vertices, pose, left, top, front, 0.0f, 0.0f, light, -1, 0, 0);
                    // Tranche droite.
                    vertex(vertices, pose, right, bottom, back, 0.0f, 1.0f, light, 1, 0, 0);
                    vertex(vertices, pose, right, bottom, front, 1.0f, 1.0f, light, 1, 0, 0);
                    vertex(vertices, pose, right, top, front, 1.0f, 0.0f, light, 1, 0, 0);
                    vertex(vertices, pose, right, top, back, 0.0f, 0.0f, light, 1, 0, 0);
                    // Tranche basse.
                    vertex(vertices, pose, left, bottom, front, 0.0f, 1.0f, light, 0, -1, 0);
                    vertex(vertices, pose, right, bottom, front, 1.0f, 1.0f, light, 0, -1, 0);
                    vertex(vertices, pose, right, bottom, back, 1.0f, 0.0f, light, 0, -1, 0);
                    vertex(vertices, pose, left, bottom, back, 0.0f, 0.0f, light, 0, -1, 0);
                    // Tranche haute.
                    vertex(vertices, pose, right, top, front, 0.0f, 1.0f, light, 0, 1, 0);
                    vertex(vertices, pose, left, top, front, 1.0f, 1.0f, light, 0, 1, 0);
                    vertex(vertices, pose, left, top, back, 1.0f, 0.0f, light, 0, 1, 0);
                    vertex(vertices, pose, right, top, back, 0.0f, 0.0f, light, 0, 1, 0);
                });
    }

    private static void submitQuad(SubmitNodeCollector collector, PoseStack poseStack,
                                   Identifier texture, float left, float bottom,
                                   float right, float top, float z, int light) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture),
                (pose, vertices) -> {
                    // Vue depuis la pièce : +X local est la gauche de l'écran.
                    vertex(vertices, pose, right, bottom, z, 0.0f, 1.0f, light, 0, 0, -1);
                    vertex(vertices, pose, left, bottom, z, 1.0f, 1.0f, light, 0, 0, -1);
                    vertex(vertices, pose, left, top, z, 1.0f, 0.0f, light, 0, 0, -1);
                    vertex(vertices, pose, right, top, z, 0.0f, 0.0f, light, 0, 0, -1);
                });
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v, int light,
                               float normalX, float normalY, float normalZ) {
        vertices.addVertex(pose, x, y, z)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, normalX, normalY, normalZ);
    }
}
