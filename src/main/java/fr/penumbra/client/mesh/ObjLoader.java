package fr.penumbra.client.mesh;

import fr.penumbra.PenumbraMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecteur de fichiers Wavefront OBJ (0.19.0) — le format d'échange 3D le plus
 * simple qui existe, et le seul dont on ait besoin.
 *
 * <p>Ce qui est lu : {@code v} (position), {@code vt} (coordonnée de texture),
 * {@code vn} (normale) et {@code f} (face). Les faces à plus de trois sommets
 * sont triangulées en éventail — c'est correct pour tout polygone convexe, ce
 * que produisent Blender, Meshy et Blockbench par défaut.</p>
 *
 * <p>Ce qui est ignoré, volontairement : les matériaux ({@code mtllib},
 * {@code usemtl}), les groupes, les lignes, les surfaces paramétriques. Un
 * maillage Penumbra porte <b>une seule texture</b>, désignée côté Java, parce
 * que Minecraft dessine une passe par texture : gérer plusieurs matériaux
 * signifierait découper le maillage en autant de soumissions, pour un gain nul
 * sur des accessoires de décor.</p>
 *
 * <p>Tolérances : les coordonnées de texture et les normales sont optionnelles.
 * Sans {@code vt} l'objet reçoit des UV nuls (il prendra la couleur du premier
 * texel) ; sans {@code vn} les normales sont <b>recalculées par produit
 * vectoriel</b> sur chaque triangle, donc l'éclairage reste correct. Un fichier
 * exporté à la va-vite fonctionne quand même.</p>
 *
 * <p>Robustesse : une ligne malformée est ignorée sans faire échouer le
 * chargement. Un modèle de décor ne doit jamais empêcher le jeu de démarrer.</p>
 */
@Environment(EnvType.CLIENT)
final class ObjLoader {

    /** Garde-fou : au-delà, on refuse le fichier au lieu de saturer la mémoire. */
    private static final int MAX_TRIANGLES = 200_000;

    private ObjLoader() {
    }

    static ObjMesh parse(BufferedReader reader, String name) throws IOException {
        List<float[]> positions = new ArrayList<>();
        List<float[]> uvs = new ArrayList<>();
        List<float[]> normals = new ArrayList<>();

        // Sommets finaux, déjà triangulés et déréférencés.
        List<float[]> outPositions = new ArrayList<>();
        List<float[]> outUvs = new ArrayList<>();
        List<float[]> outNormals = new ArrayList<>();

        int skipped = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.charAt(0) == '#') {
                continue;
            }
            String[] parts = line.split("\\s+");
            try {
                switch (parts[0]) {
                    case "v" -> positions.add(new float[] {
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])});
                    case "vt" -> uvs.add(new float[] {
                            Float.parseFloat(parts[1]),
                            // OBJ compte V depuis le bas, Minecraft depuis le
                            // haut : c'est l'unique conversion de repère du
                            // chargeur, et l'oublier retourne toutes les textures.
                            1.0f - Float.parseFloat(parts[2])});
                    case "vn" -> normals.add(new float[] {
                            Float.parseFloat(parts[1]),
                            Float.parseFloat(parts[2]),
                            Float.parseFloat(parts[3])});
                    case "f" -> {
                        if (parts.length < 4) {
                            skipped++;
                            break;
                        }
                        // Triangulation en éventail : (0,1,2), (0,2,3), (0,3,4)…
                        for (int corner = 2; corner < parts.length - 1; corner++) {
                            if (outPositions.size() / 3 >= MAX_TRIANGLES) {
                                throw new IOException("plus de " + MAX_TRIANGLES
                                        + " triangles : fichier refusé");
                            }
                            emit(parts[1], positions, uvs, normals,
                                    outPositions, outUvs, outNormals);
                            emit(parts[corner], positions, uvs, normals,
                                    outPositions, outUvs, outNormals);
                            emit(parts[corner + 1], positions, uvs, normals,
                                    outPositions, outUvs, outNormals);
                        }
                    }
                    default -> {
                        // mtllib, usemtl, o, g, s… : sans objet ici.
                    }
                }
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException malformed) {
                skipped++;
            }
        }

        if (skipped > 0) {
            PenumbraMod.LOGGER.warn("[Penumbra] {} : {} lignes ignorées à la lecture",
                    name, skipped);
        }
        fixMissingNormals(outPositions, outNormals);
        return new ObjMesh(flatten(outPositions, 3), flatten(outUvs, 2),
                flatten(outNormals, 3));
    }

    /**
     * Déréférence un sommet {@code v/vt/vn}. Les index OBJ commencent à 1 et
     * peuvent être négatifs (comptés depuis la fin) — les deux cas sont gérés.
     */
    private static void emit(String token,
                             List<float[]> positions, List<float[]> uvs, List<float[]> normals,
                             List<float[]> outPositions, List<float[]> outUvs,
                             List<float[]> outNormals) {
        String[] fields = token.split("/", -1);
        outPositions.add(resolve(positions, fields[0], new float[] {0, 0, 0}));
        outUvs.add(fields.length > 1
                ? resolve(uvs, fields[1], new float[] {0, 0})
                : new float[] {0, 0});
        outNormals.add(fields.length > 2
                ? resolve(normals, fields[2], null)
                : null);
    }

    private static float[] resolve(List<float[]> source, String field, float[] fallback) {
        if (field == null || field.isEmpty()) {
            return fallback;
        }
        int index = Integer.parseInt(field);
        // Index négatif : -1 désigne le dernier élément déclaré.
        int resolved = index < 0 ? source.size() + index : index - 1;
        if (resolved < 0 || resolved >= source.size()) {
            return fallback;
        }
        return source.get(resolved);
    }

    /**
     * Remplace les normales absentes par la normale géométrique du triangle.
     * Sans ça, un OBJ exporté sans {@code vn} serait rendu entièrement noir ou
     * entièrement plat selon le shader — c'est le défaut le plus fréquent des
     * fichiers trouvés en ligne.
     */
    private static void fixMissingNormals(List<float[]> positions, List<float[]> normals) {
        for (int triangle = 0; triangle + 2 < positions.size(); triangle += 3) {
            if (normals.get(triangle) != null
                    && normals.get(triangle + 1) != null
                    && normals.get(triangle + 2) != null) {
                continue;
            }
            float[] a = positions.get(triangle);
            float[] b = positions.get(triangle + 1);
            float[] c = positions.get(triangle + 2);
            float ux = b[0] - a[0];
            float uy = b[1] - a[1];
            float uz = b[2] - a[2];
            float vx = c[0] - a[0];
            float vy = c[1] - a[1];
            float vz = c[2] - a[2];
            float nx = uy * vz - uz * vy;
            float ny = uz * vx - ux * vz;
            float nz = ux * vy - uy * vx;
            float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
            float[] normal = length < 1.0E-6f
                    ? new float[] {0.0f, 1.0f, 0.0f}
                    : new float[] {nx / length, ny / length, nz / length};
            for (int corner = 0; corner < 3; corner++) {
                if (normals.get(triangle + corner) == null) {
                    normals.set(triangle + corner, normal);
                }
            }
        }
        // Filet de sécurité : un triangle incomplet en fin de fichier.
        for (int index = 0; index < normals.size(); index++) {
            if (normals.get(index) == null) {
                normals.set(index, new float[] {0.0f, 1.0f, 0.0f});
            }
        }
    }

    /** Aplatit une liste de petits tableaux en un seul, sans copie superflue. */
    private static float[] flatten(List<float[]> source, int stride) {
        // Le nombre de sommets doit être un multiple de trois : on tronque le
        // reste plutôt que de dessiner un triangle incomplet.
        int vertices = source.size() - source.size() % 3;
        float[] out = new float[vertices * stride];
        for (int index = 0; index < vertices; index++) {
            System.arraycopy(source.get(index), 0, out, index * stride, stride);
        }
        return out;
    }
}
