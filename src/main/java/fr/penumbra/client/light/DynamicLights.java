package fr.penumbra.client.light;

import fr.penumbra.client.ambient.ClientTensionState;
import fr.penumbra.config.PenumbraConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Éclairage dynamique porté à la main (0.19.0) — la torche éclaire enfin.
 *
 * <h2>Pourquoi ça n'existe pas dans le jeu de base</h2>
 * <p>La lumière de bloc de Minecraft n'est pas calculée à l'image : elle est
 * <b>cuite dans les sommets</b> de chaque section de terrain au moment où sa
 * géométrie est construite. Une source qui bouge oblige donc à reconstruire la
 * géométrie autour d'elle — ce qui coûte cher, et c'est exactement la raison
 * pour laquelle Mojang ne le fait pas.</p>
 *
 * <h2>Le point d'accroche</h2>
 * <p>Tout le rendu passe par {@code LevelRenderer.getLightCoords} : le terrain
 * via {@code ModelBlockRenderer}, mais aussi les entités et les particules. Un
 * seul mixin sur cette méthode suffit donc à éclairer <i>toute</i> la scène,
 * sans toucher au moteur de lumière ni au serveur. Le calcul reste purement
 * client : rien n'est envoyé, rien n'est sauvegardé, aucun bloc n'est posé.</p>
 *
 * <h2>Ce que Penumbra en fait de particulier</h2>
 * <p>Une torche qui éclaire proprement est un confort. Une torche <b>qui faiblit
 * quand la tension monte</b> est une horloge : le joueur voit son rayon de
 * sécurité se rétracter sans qu'on lui dise pourquoi. C'est le seul intérêt
 * d'écrire ça ici plutôt que d'installer un mod d'éclairage générique.</p>
 *
 * <p>Le vacillement est <b>quantifié à l'entier</b> et ne change qu'une fois
 * tous les {@value #FLICKER_PERIOD_TICKS} ticks : sans cette contrainte, chaque
 * image demanderait une reconstruction du terrain et le jeu s'effondrerait.</p>
 */
@Environment(EnvType.CLIENT)
public final class DynamicLights {

    /** Portée maximale d'une source, en blocs. Au-delà, l'effet ne se voit plus. */
    private static final int MAX_LUMINANCE = 15;
    /** Le vacillement ne se recalcule qu'à cette cadence (voir en-tête). */
    private static final int FLICKER_PERIOD_TICKS = 5;

    private static final List<Source> SOURCES = new ArrayList<>();
    private static final List<Source> PREVIOUS = new ArrayList<>();

    /**
     * Instantané publié pour la lecture.
     *
     * <p>Indispensable, et pas par prudence : {@link #blockLightAt} est appelé
     * depuis les <b>fils de compilation des sections</b>, pendant que le tick
     * client réécrit la liste. Faire lire un {@code ArrayList} en cours de
     * modification produirait des exceptions aléatoires en pleine génération de
     * terrain. Un tableau immuable republié à chaque tick, lu une seule fois par
     * appel, supprime la course sans aucun verrou.</p>
     */
    private static volatile Source[] published = new Source[0];

    private static int tickCounter;
    private static int flickerOffset;

    private DynamicLights() {
    }

    /** Une source lumineuse mobile : une position et une intensité. */
    private record Source(double x, double y, double z, int luminance) {

        LightSectionUpdates.Cell cell() {
            return new LightSectionUpdates.Cell((int) Math.floor(x), (int) Math.floor(y),
                    (int) Math.floor(z), luminance);
        }

    }

    /** À brancher sur {@code ClientTickEvents.END_CLIENT_TICK}. */
    public static void onClientTick(Minecraft client) {
        if (!PenumbraConfig.get().dynamicLightsEnabled
                || client.level == null) {
            if (!SOURCES.isEmpty()) {
                clear(client);
            }
            return;
        }

        // A paused solo world keeps its lighting; reopening a menu must not
        // extinguish every torch and schedule all nearby terrain for rebuilding.
        if (client.isPaused()) {
            return;
        }
        if (++tickCounter % FLICKER_PERIOD_TICKS == 0) {
            flickerOffset = computeFlicker();
        }

        PREVIOUS.clear();
        PREVIOUS.addAll(SOURCES);
        SOURCES.clear();

        for (Entity entity : client.level.entitiesForRendering()) {
            int luminance = luminanceOf(entity);
            if (luminance > 0) {
                SOURCES.add(new Source(entity.getX(),
                        entity.getEyeY() - 0.2, entity.getZ(),
                        Math.max(0, Math.min(MAX_LUMINANCE, luminance + flickerOffset))));
            }
        }

        // Publish before workers can rebuild the invalidated sections.
        published = SOURCES.toArray(new Source[0]);
        invalidateChanged(client);
    }

    /**
     * Lumière de bloc apportée par les sources mobiles à cette position.
     *
     * <p>Appelée pour <b>chaque face de bloc</b> pendant la compilation d'une
     * section, donc des millions de fois par reconstruction. Elle sort donc
     * immédiatement quand il n'y a aucune source — le cas de très loin le plus
     * fréquent — et n'appelle {@code sqrt} qu'après avoir écarté la source par
     * un test de boîte englobante, puis de distance au carré.</p>
     */
    public static int blockLightAt(BlockPos pos) {
        Source[] sources = published;
        if (sources.length == 0) {
            return 0;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        int best = 0;
        for (Source source : sources) {
            int reach = source.luminance;
            if (reach <= best) {
                continue;               // ne pourra pas améliorer le résultat
            }
            double dx = source.x - x;
            if (dx > reach || dx < -reach) {
                continue;
            }
            double dy = source.y - y;
            if (dy > reach || dy < -reach) {
                continue;
            }
            double dz = source.z - z;
            if (dz > reach || dz < -reach) {
                continue;
            }
            double squared = dx * dx + dy * dy + dz * dz;
            if (squared >= (double) reach * reach) {
                continue;
            }
            // Décroissance linéaire, comme la lumière de bloc vanilla : un
            // niveau perdu par bloc parcouru.
            int value = (int) (reach - Math.sqrt(squared));
            if (value > best) {
                best = value;
            }
        }
        return Math.min(best, MAX_LUMINANCE);
    }

    /**
     * Intensité qu'émet une entité. Un joueur éclaire avec ce qu'il tient ; un
     * objet au sol éclaire aussi, ce qui rend une torche jetée réellement utile.
     */
    private static int luminanceOf(Entity entity) {
        if (entity.isSpectator() || entity.isInvisible()) {
            return 0;
        }
        if (entity instanceof ItemEntity item) {
            return luminanceOf(item.getItem());
        }
        if (entity instanceof LivingEntity living) {
            int held = Math.max(
                    luminanceOf(living.getMainHandItem()),
                    luminanceOf(living.getOffhandItem()));
            if (entity instanceof Player && living.isOnFire()) {
                held = Math.max(held, 10);
            }
            return held;
        }
        return 0;
    }

    /**
     * Intensité d'un objet, lue sur le bloc qu'il pose. Aucune liste en dur :
     * torche, torche des âmes, lanterne, pierre lumineuse, chandelle, tout ce
     * qu'ajoute un autre mod — si le bloc éclaire, l'objet éclaire.
     */
    private static int luminanceOf(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem)) {
            return 0;
        }
        BlockState state = blockItem.getBlock().defaultBlockState();
        return state.getLightEmission();
    }

    /**
     * Vacillement piloté par la tension.
     *
     * <p>Au calme, rien. Plus la tension monte, plus la flamme perd de sa
     * portée et plus elle bat. C'est une information que le joueur reçoit sans
     * texte ni son : son rayon de sécurité se rétracte.</p>
     */
    private static int computeFlicker() {
        float tension = ClientTensionState.tension();
        if (!PenumbraConfig.get().dynamicLightFlicker || tension < 25.0f) {
            return 0;
        }
        double severity = Math.min(1.0, (tension - 25.0) / 65.0);
        double roll = Math.random();
        if (roll < 0.06 * severity) {
            return -5;                      // la flamme manque de s'éteindre
        }
        if (roll < 0.34 * severity) {
            return -2;
        }
        return severity > 0.5 && roll > 0.94 ? -1 : 0;
    }

    /**
     * Reconstruit les sections autour des sources qui ont changé de bloc ou
     * d'intensité. C'est l'opération coûteuse : elle ne doit jamais se déclencher
     * quand rien n'a bougé, d'où la comparaison avec l'état de la frame
     * précédente plutôt qu'une reconstruction systématique.
     */
    private static void invalidateChanged(Minecraft client) {
        if (client.levelRenderer == null) {
            return;
        }
        var previous = PREVIOUS.stream().map(Source::cell).collect(java.util.stream.Collectors.toSet());
        var current = SOURCES.stream().map(Source::cell).collect(java.util.stream.Collectors.toSet());
        for (var section : LightSectionUpdates.changed(previous, current)) {
            client.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
        }
    }

    /** Éteint tout et reconstruit une dernière fois : sortie de monde, option coupée. */
    public static void clear(Minecraft client) {
        PREVIOUS.clear();
        PREVIOUS.addAll(SOURCES);
        SOURCES.clear();
        published = new Source[0];
        invalidateChanged(client);
        PREVIOUS.clear();
        flickerOffset = 0;
    }

    /** Nombre de sources actives, pour les commandes de diagnostic. */
    public static int sourceCount() {
        return published.length;
    }

    /** Décalage de vacillement courant, pour les commandes de diagnostic. */
    public static int flicker() {
        return flickerOffset;
    }
}
