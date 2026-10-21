package fr.penumbra.client.photo;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Transformation;
import fr.penumbra.PenumbraMod;
import fr.penumbra.photo.PhotoContent;
import fr.penumbra.photo.PhotoData;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelModifier;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Affiche la vraie capture sur la surface des objets photo, dans tous les contextes. */
public final class PhotoItemRendering {

    private static final Set<Identifier> PHOTO_ITEM_IDS = Set.of(
            PenumbraMod.id("camera_photo_black"),
            PenumbraMod.id("camera_photo_faint"),
            PenumbraMod.id("camera_photo_sepia"),
            PenumbraMod.id("camera_photo"));

    private PhotoItemRendering() {
    }

    public static void register() {
        ModelLoadingPlugin.register(context -> context.modifyItemModelAfterBake()
                .register(PhotoItemRendering::wrapPhotoModel));
    }

    private static ItemModel wrapPhotoModel(ItemModel original,
                                            ModelModifier.AfterBakeItem.Context context) {
        if (!PHOTO_ITEM_IDS.contains(context.itemId())) {
            return original;
        }

        // Les quatre definitions utilisent "minecraft:model". En recuperant le
        // modele source, la surimpression herite exactement des transformations
        // GUI/main gauche-droite/sol du modele statique, sans mixin de renderer.
        if (!(context.sourceModel() instanceof CuboidItemModelWrapper.Unbaked source)) {
            PenumbraMod.LOGGER.warn("[Penumbra] Modele photo dynamique ignore pour {} : type {}",
                    context.itemId(), context.sourceModel().getClass().getName());
            return original;
        }

        ModelBaker baker = context.bakingContext().blockModelBaker();
        ResolvedModel resolved = baker.getModel(source.model());
        TextureSlots textureSlots = resolved.getTopTextureSlots();
        ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(
                baker, resolved, textureSlots);
        Matrix4fc transformation = Transformation.compose(
                context.transformation(), source.transformation());
        return new DynamicPhotoItemModel(original, properties, transformation);
    }

    private static final class DynamicPhotoItemModel implements ItemModel {

        private final ItemModel original;
        private final ModelRenderProperties properties;
        private final Matrix4fc transformation;

        private DynamicPhotoItemModel(ItemModel original, ModelRenderProperties properties,
                                      Matrix4fc transformation) {
            this.original = original;
            this.properties = properties;
            this.transformation = transformation;
        }

        @Override
        public void update(ItemStackRenderState state, ItemStack stack,
                           ItemModelResolver resolver, ItemDisplayContext displayContext,
                           ClientLevel level, ItemOwner owner, int seed) {
            original.update(state, stack, resolver, displayContext, level, owner, seed);
            PhotoData photo = stack.get(PhotoContent.PHOTO_DATA);
            if (photo == null) {
                return;
            }

            PhotoClientStore.ensureRequested(photo.photoId());
            state.appendModelIdentityElement(photo.photoId());
            // Les objets GUI sont normalement mis en cache. La texture chimique
            // change pendant dix secondes : forcer ce seul objet a etre anime
            // permet de voir chaque etape sans attendre un changement de stack.
            state.setAnimated();
            ItemStackRenderState.LayerRenderState layer = state.newLayer();
            layer.setLocalTransform(transformation);
            layer.setExtents(PhotoSurfaceRenderer.EXTENTS);
            layer.setupSpecialModel(PhotoSurfaceRenderer.INSTANCE, photo);
            properties.applyToLayer(layer, displayContext);
        }
    }

    private static final class PhotoSurfaceRenderer implements SpecialModelRenderer<PhotoData> {

        private static final PhotoSurfaceRenderer INSTANCE = new PhotoSurfaceRenderer();

        // Coordonnees du modele generated (0..1). La capture 16:9 reste non deformee
        // et se loge dans la fenetre sombre de la texture Polaroid 64x64.
        private static final float LEFT = 0.1875f;
        private static final float RIGHT = 0.8125f;
        private static final float BOTTOM = 0.390625f;
        private static final float TOP = 0.7421875f;
        private static final float Z = 0.5325f;
        private static final Vector3fc[] SURFACE_EXTENTS = {
                new Vector3f(LEFT, BOTTOM, Z),
                new Vector3f(RIGHT, BOTTOM, Z),
                new Vector3f(RIGHT, TOP, Z),
                new Vector3f(LEFT, TOP, Z)
        };
        private static final Supplier<Vector3fc[]> EXTENTS = () -> SURFACE_EXTENTS;

        private PhotoSurfaceRenderer() {
        }

        @Override
        public void submit(PhotoData photo, PoseStack poseStack, SubmitNodeCollector collector,
                           int light, int overlay, boolean foil, int outlineColor) {
            if (photo == null) {
                return;
            }
            Identifier texture = PhotoClientStore.texture(photo.photoId());
            if (texture == null) {
                return;
            }

            collector.submitCustomGeometry(poseStack, RenderTypes.itemCutout(texture),
                    (pose, vertices) -> {
                        vertex(vertices, pose, LEFT, BOTTOM, Z, 0.0f, 1.0f, light, overlay);
                        vertex(vertices, pose, RIGHT, BOTTOM, Z, 1.0f, 1.0f, light, overlay);
                        vertex(vertices, pose, RIGHT, TOP, Z, 1.0f, 0.0f, light, overlay);
                        vertex(vertices, pose, LEFT, TOP, Z, 0.0f, 0.0f, light, overlay);
                    });
        }

        @Override
        public void getExtents(Consumer<Vector3fc> consumer) {
            for (Vector3fc extent : SURFACE_EXTENTS) {
                consumer.accept(extent);
            }
        }

        @Override
        public PhotoData extractArgument(ItemStack stack) {
            return stack.get(PhotoContent.PHOTO_DATA);
        }

        private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                                   float x, float y, float z, float u, float v,
                                   int light, int overlay) {
            vertices.addVertex(pose, x, y, z)
                    .setColor(0xFFFFFFFF)
                    .setUv(u, v)
                    .setOverlay(overlay)
                    .setLight(light)
                    .setNormal(pose, 0.0f, 0.0f, 1.0f);
        }
    }
}
