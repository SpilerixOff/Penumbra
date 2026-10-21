package fr.penumbra.entity.absent;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.penumbra.PenumbraMod;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Registre des identités que L'Absent peut porter (0.19.0).
 *
 * <p><b>Le problème qu'il résout.</b> L'Absent imitait le témoin lui-même dès
 * qu'aucun autre joueur n'était connecté — c'est-à-dire à peu près toujours, en
 * solo. Or se voir soi-même ne fait pas peur : on reconnaît son propre skin en
 * un dixième de seconde, la scène est éventée avant d'avoir commencé. Ce qui
 * fonctionne, c'est <b>quelqu'un d'autre</b> : un joueur qui était passé sur ce
 * monde il y a longtemps, ou un inconnu dont on ne s'explique pas la présence.</p>
 *
 * <p><b>Trois sources, dans cet ordre.</b></p>
 * <ol>
 *   <li>Un autre joueur <i>connecté</i> — géré par {@code AbsentManager}, qui a
 *       accès à son équipement et à sa position réelle.</li>
 *   <li>Un joueur qui <b>s'est déjà connecté à ce monde</b>. Chaque arrivée est
 *       enregistrée ici. Sur un serveur, c'est la source la plus perturbante :
 *       le nom est familier, la personne n'est pas là.</li>
 *   <li>Un <b>inconnu</b>, quand le monde n'a jamais vu personne d'autre. Son
 *       nom est tiré de la graine du monde : il ne change jamais. Le même
 *       étranger revient à chaque scène, et c'est très précisément ce qui rend
 *       la chose inquiétante plutôt qu'aléatoire.</li>
 * </ol>
 *
 * <p>Le témoin n'est <b>jamais</b> retenu, quelle que soit la source.</p>
 */
public class KnownIdentities extends SavedData {

    private static final String DATA_NAME = "penumbra_identities";
    /** Au-delà, éviction FIFO : un serveur public ne doit pas gonfler la sauvegarde. */
    private static final int MAX_TRACKED = 128;

    /**
     * Noms d'inconnus. Volontairement ordinaires — un pseudo qui « fait
     * horreur » se lit comme un décor de mod, alors qu'un pseudo banal se lit
     * comme quelqu'un qui a vraiment joué ici avant vous.
     */
    private static final List<String> STRANGERS = List.of(
            "Elias_W", "mattgrn", "T_Hallward", "pale_owl", "noah_k",
            "Sixte", "lantern42", "R_Ashby", "grey_march", "Corvin",
            "adele_v", "still_water", "M_Thorne", "nightjar", "Ewen__");

    public static final Codec<KnownIdentities> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING.xmap(UUID::fromString, UUID::toString), Codec.STRING)
                    .optionalFieldOf("seen", Map.of())
                    .forGetter(registry -> Map.copyOf(registry.names))
    ).apply(instance, KnownIdentities::new));

    private static final SavedDataType<KnownIdentities> TYPE =
            new SavedDataType<>(PenumbraMod.id(DATA_NAME), KnownIdentities::new, CODEC, null);

    private final Map<UUID, String> names;

    public KnownIdentities() {
        this(Map.of());
    }

    private KnownIdentities(Map<UUID, String> names) {
        this.names = new LinkedHashMap<>(names);
    }

    public static KnownIdentities get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** À appeler à chaque connexion. Idempotent. */
    public static void remember(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        if (server == null) {
            return;
        }
        KnownIdentities registry = get(server);
        String name = player.getGameProfile().name();
        if (name == null || name.isBlank()) {
            return;
        }
        if (name.equals(registry.names.put(player.getUUID(), name))) {
            return;                       // déjà connu sous ce nom
        }
        while (registry.names.size() > MAX_TRACKED) {
            registry.names.remove(registry.names.keySet().iterator().next());
        }
        registry.setDirty();
    }

    /**
     * Une identité pour L'Absent, jamais celle du témoin.
     *
     * @param allowPastPlayers  {@code false} reste sur un inconnu inventé, sans
     *                          jamais réutiliser le pseudo d'une personne réelle
     *                          (c'est ce que demande {@code multiplayerContent}
     *                          désactivé).
     */
    public static GameProfile pick(MinecraftServer server, ServerPlayer owner,
                                   RandomSource random, boolean allowPastPlayers) {
        if (allowPastPlayers) {
            List<Map.Entry<UUID, String>> candidates = new ArrayList<>();
            for (Map.Entry<UUID, String> entry : get(server).names.entrySet()) {
                if (!entry.getKey().equals(owner.getUUID())) {
                    candidates.add(entry);
                }
            }
            if (!candidates.isEmpty()) {
                Map.Entry<UUID, String> chosen =
                        candidates.get(random.nextInt(candidates.size()));
                // On garde l'UUID réel : le client résout alors le vrai skin de
                // cette personne. C'est tout l'intérêt — un visage connu.
                return new GameProfile(chosen.getKey(), chosen.getValue());
            }
        }
        return stranger(server);
    }

    /**
     * L'inconnu de ce monde. Dérivé de la graine, donc stable : c'est toujours
     * le même qui revient, ce qui transforme un pseudo aléatoire en présence.
     */
    public static GameProfile stranger(MinecraftServer server) {
        long seed = server.overworld().getSeed();
        long mixed = seed * 0x9E3779B97F4A7C15L;
        mixed ^= mixed >>> 31;
        String name = STRANGERS.get((int) Math.floorMod(mixed, STRANGERS.size()));
        // UUID synthétique : aucun compte réel n'est usurpé, et le client
        // retombe sur un skin par défaut — un inconnu, littéralement.
        UUID id = UUID.nameUUIDFromBytes(
                ("PenumbraStranger:" + name).getBytes(StandardCharsets.UTF_8));
        return new GameProfile(id, name);
    }

    /** Nombre d'identités connues de ce monde, pour les commandes de diagnostic. */
    public int size() {
        return this.names.size();
    }
}
