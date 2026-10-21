package fr.penumbra.client.mesh;

import fr.penumbra.PenumbraMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.BufferedReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Cache des maillages chargés depuis les ressources (0.19.0).
 *
 * <p>Un fichier est lu <b>au premier rendu qui le demande</b>, jamais au
 * démarrage : un joueur qui ne croise aucun accessoire ne paie rien. Le
 * résultat est mémorisé — y compris l'échec, sous forme de maillage vide — pour
 * qu'un fichier manquant ne provoque pas une tentative de lecture à chaque
 * frame.</p>
 *
 * <p>Les fichiers vivent dans {@code assets/penumbra/meshes/<nom>.obj} et
 * passent par le gestionnaire de ressources vanilla : <b>un pack de ressources
 * peut donc les remplacer</b> sans toucher au mod. C'est ce qui rend le système
 * réellement utilisable par quelqu'un d'autre.</p>
 *
 * <p>Uniquement côté client, et uniquement sur le fil de rendu : la table n'est
 * pas synchronisée, car rien d'autre n'y touche.</p>
 */
@Environment(EnvType.CLIENT)
public final class MeshLibrary {

    private static final Map<Identifier, ObjMesh> CACHE = new HashMap<>();

    private MeshLibrary() {
    }

    /**
     * Maillage nommé, chargé à la demande. Retourne un maillage vide plutôt que
     * {@code null} : le renderer n'a ainsi jamais de cas particulier à traiter,
     * et un fichier absent produit un objet invisible au lieu d'un plantage.
     *
     * @param name nom court, sans dossier ni extension (ex. {@code "effigy"})
     */
    public static ObjMesh get(String name) {
        Identifier id = PenumbraMod.id("meshes/" + name + ".obj");
        ObjMesh cached = CACHE.get(id);
        if (cached != null) {
            return cached;
        }
        ObjMesh loaded = load(id);
        CACHE.put(id, loaded);
        return loaded;
    }

    private static ObjMesh load(Identifier id) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) {
            return ObjMesh.empty();
        }
        Optional<Resource> resource = client.getResourceManager().getResource(id);
        if (resource.isEmpty()) {
            PenumbraMod.LOGGER.warn("[Penumbra] maillage introuvable : {}", id);
            return ObjMesh.empty();
        }
        try (BufferedReader reader = resource.get().openAsReader()) {
            ObjMesh mesh = ObjLoader.parse(reader, id.toString());
            PenumbraMod.LOGGER.info("[Penumbra] maillage {} chargé : {} triangles, "
                            + "hauteur {}–{}, rayon {}",
                    id, mesh.triangleCount(),
                    String.format("%.2f", mesh.minY()),
                    String.format("%.2f", mesh.maxY()),
                    String.format("%.2f", mesh.radius()));
            return mesh;
        } catch (Exception failure) {
            // Un accessoire de décor ne doit jamais faire tomber le client.
            PenumbraMod.LOGGER.error("[Penumbra] maillage {} illisible", id, failure);
            return ObjMesh.empty();
        }
    }

    /**
     * Vide le cache. À appeler après un rechargement de ressources (F3+T) pour
     * que les modifications d'un pack soient prises en compte sans redémarrer.
     */
    public static void clear() {
        CACHE.clear();
    }

    /** Nombre de maillages actuellement en mémoire, pour le diagnostic. */
    public static int size() {
        return CACHE.size();
    }
}
