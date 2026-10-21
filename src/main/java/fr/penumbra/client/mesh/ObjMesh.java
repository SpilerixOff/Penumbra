package fr.penumbra.client.mesh;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Un maillage triangulé prêt à être envoyé au GPU (0.19.0).
 *
 * <p>Les données sont stockées à plat, un {@code float[]} par attribut, dans
 * l'ordre exact des sommets : trois sommets consécutifs forment un triangle.
 * C'est volontairement le format le plus bête possible — il n'y a ni index, ni
 * objet par sommet, ni allocation au moment du rendu. Un maillage de dix mille
 * triangles se parcourt donc sans produire un seul déchet mémoire par frame,
 * ce qui compte quand on dessine à soixante images par seconde.</p>
 *
 * <p>Repère : celui de Minecraft, en blocs. L'origine est le point d'ancrage de
 * l'objet, {@code +Y} vers le haut. Le chargeur convertit depuis le repère OBJ
 * (métrique, {@code +Y} vers le haut aussi) sans rien changer d'autre : un
 * cube d'un mètre dans Blender fera un bloc en jeu.</p>
 */
@Environment(EnvType.CLIENT)
public final class ObjMesh {

    private final float[] positions;
    private final float[] uvs;
    private final float[] normals;
    private final int triangleCount;
    private final float minY;
    private final float maxY;
    private final float radius;

    ObjMesh(float[] positions, float[] uvs, float[] normals) {
        this.positions = positions;
        this.uvs = uvs;
        this.normals = normals;
        this.triangleCount = positions.length / 9;

        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        float farthest = 0.0f;
        for (int index = 0; index < positions.length; index += 3) {
            float x = positions[index];
            float y = positions[index + 1];
            float z = positions[index + 2];
            low = Math.min(low, y);
            high = Math.max(high, y);
            farthest = Math.max(farthest, x * x + y * y + z * z);
        }
        this.minY = this.triangleCount == 0 ? 0.0f : low;
        this.maxY = this.triangleCount == 0 ? 0.0f : high;
        this.radius = (float) Math.sqrt(farthest);
    }

    /** Maillage vide : rendu par le chargeur quand un fichier est absent ou illisible. */
    static ObjMesh empty() {
        return new ObjMesh(new float[0], new float[0], new float[0]);
    }

    public int triangleCount() {
        return this.triangleCount;
    }

    public boolean isEmpty() {
        return this.triangleCount == 0;
    }

    /** Hauteur du maillage, utile pour poser une plaque de sol ou une ombre. */
    public float minY() {
        return this.minY;
    }

    public float maxY() {
        return this.maxY;
    }

    /** Rayon englobant depuis l'origine, pour le calcul de visibilité. */
    public float radius() {
        return this.radius;
    }

    // Accès bruts, réservés au renderer : aucune copie, aucune vérification.
    float px(int vertex) {
        return this.positions[vertex * 3];
    }

    float py(int vertex) {
        return this.positions[vertex * 3 + 1];
    }

    float pz(int vertex) {
        return this.positions[vertex * 3 + 2];
    }

    float u(int vertex) {
        return this.uvs[vertex * 2];
    }

    float v(int vertex) {
        return this.uvs[vertex * 2 + 1];
    }

    float nx(int vertex) {
        return this.normals[vertex * 3];
    }

    float ny(int vertex) {
        return this.normals[vertex * 3 + 1];
    }

    float nz(int vertex) {
        return this.normals[vertex * 3 + 2];
    }
}
