package fr.penumbra.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Envoie un {@link ObjMesh} au moteur de rendu (0.19.0).
 *
 * <p>Passe par {@code SubmitNodeCollector.submitCustomGeometry}, la porte de
 * sortie officielle de 26.1 pour la géométrie arbitraire — la même que celle
 * déjà utilisée par {@code PhotoFrameRenderer}. Aucun mixin, aucun accès à un
 * buffer interne : le maillage traverse exactement le même chemin que le reste
 * du jeu, donc il reçoit l'éclairage, le tri de transparence et le brouillard
 * sans rien de particulier à écrire.</p>
 *
 * <p><b>Le point à connaître :</b> les types de rendu d'entité de Minecraft
 * travaillent en <em>quads</em>, quatre sommets par face. Un triangle est donc
 * émis avec son troisième sommet répété — un quad dégénéré. Le GPU le traite
 * comme un triangle d'aire nulle collé au précédent, ce qui est invisible et ne
 * coûte rien. C'est la méthode standard, mais elle surprend quand on lit le
 * code pour la première fois.</p>
 */
@Environment(EnvType.CLIENT)
public final class MeshRenderer {

    private MeshRenderer() {
    }

    /**
     * Dessine un maillage texturé à la position courante de la pile de matrices.
     *
     * @param light éclairage empaqueté ({@code lightCoords} de l'état de rendu)
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack,
                              ObjMesh mesh, Identifier texture, int light) {
        submit(collector, poseStack, mesh, texture, light, 0xFFFFFFFF);
    }

    /**
     * Variante teintée. La couleur multiplie la texture : {@code 0xFFFFFFFF}
     * laisse l'objet intact, une valeur plus sombre l'assombrit sans toucher au
     * fichier — pratique pour patiner une même statue selon l'endroit.
     */
    public static void submit(SubmitNodeCollector collector, PoseStack poseStack,
                              ObjMesh mesh, Identifier texture, int light, int argb) {
        if (mesh.isEmpty()) {
            return;
        }
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture),
                (pose, vertices) -> emit(pose, vertices, mesh, light, argb));
    }

    private static void emit(PoseStack.Pose pose, VertexConsumer vertices,
                             ObjMesh mesh, int light, int argb) {
        int triangles = mesh.triangleCount();
        for (int triangle = 0; triangle < triangles; triangle++) {
            int base = triangle * 3;
            vertex(vertices, pose, mesh, base, light, argb);
            vertex(vertices, pose, mesh, base + 1, light, argb);
            vertex(vertices, pose, mesh, base + 2, light, argb);
            // Quatrième sommet du quad : on répète le troisième. Le triangle
            // supplémentaire a une aire nulle, il ne produit aucun pixel.
            vertex(vertices, pose, mesh, base + 2, light, argb);
        }
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               ObjMesh mesh, int index, int light, int argb) {
        vertices.addVertex(pose, mesh.px(index), mesh.py(index), mesh.pz(index))
                .setColor(argb)
                .setUv(mesh.u(index), mesh.v(index))
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, mesh.nx(index), mesh.ny(index), mesh.nz(index));
    }
}
